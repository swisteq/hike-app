package pl.gorskie.wyprawy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gorskie.wyprawy.dto.GroupDto;
import pl.gorskie.wyprawy.model.Group;
import pl.gorskie.wyprawy.model.GroupMember;
import pl.gorskie.wyprawy.model.GroupMessage;
import pl.gorskie.wyprawy.model.Notification.NotificationType;
import pl.gorskie.wyprawy.model.User;
import pl.gorskie.wyprawy.repository.*;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public Group create(GroupDto.CreateRequest req, Long ownerId) {
        User owner = findUser(ownerId);
        Group group = Group.builder()
                .name(req.getName())
                .description(req.getDescription())
                .owner(owner)
                .build();
        return groupRepository.save(group);
    }

    @Transactional
    public Group update(Long groupId, GroupDto.UpdateRequest req, Long userId) {
        Group group = findAndCheckOwner(groupId, userId);
        if (req.getName() != null) group.setName(req.getName());
        if (req.getDescription() != null) group.setDescription(req.getDescription());
        return groupRepository.save(group);
    }

    @Transactional
    public void delete(Long groupId, Long userId) {
        findAndCheckOwner(groupId, userId);
        groupRepository.deleteById(groupId);
    }

    @Transactional(readOnly = true)
    public Group findById(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono grupy id=" + id));
    }

    @Transactional(readOnly = true)
    public List<Group> findAllPublic() {
        return groupRepository.findAllPublic();
    }

    @Transactional(readOnly = true)
    public List<Group> findAllForUser(Long userId) {
        return groupRepository.findAllForUser(userId);
    }

    public String resolveViewerRole(Group group, Long userId) {
        if (group.getOwner().getId().equals(userId)) return "OWNER";
        return group.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .map(m -> switch (m.getStatus()) {
                    case ACCEPTED -> "MEMBER";
                    case INVITED  -> "INVITED";
                    case PENDING  -> "PENDING";
                    case DECLINED -> "VISITOR";
                })
                .orElse("VISITOR");
    }

    @Transactional
    public GroupMember join(Long groupId, Long userId) {
        Group group = findById(groupId);
        if (group.getOwner().getId().equals(userId))
            throw new IllegalArgumentException("Jesteś właścicielem tej grupy");
        if (memberRepository.findByGroupIdAndUserId(groupId, userId).isPresent())
            throw new IllegalArgumentException("Jesteś już członkiem tej grupy");

        User user = findUser(userId);
        GroupMember member = GroupMember.builder()
                .group(group)
                .user(user)
                .status(GroupMember.MemberStatus.PENDING)
                .build();
        GroupMember saved = memberRepository.save(member);
        notificationService.notify(group.getOwner().getId(), NotificationType.GROUP_JOIN_REQUEST,
                user.getName() + " chce dołączyć do grupy \"" + group.getName() + "\"",
                "/groups/" + groupId);
        return saved;
    }

    @Transactional
    public GroupMember invite(Long groupId, String username, Long ownerId) {
        Group group = findAndCheckOwner(groupId, ownerId);
        User invited = userRepository.findByNameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika: " + username));
        if (invited.getId().equals(ownerId))
            throw new IllegalArgumentException("Nie można zaprosić samego siebie");
        if (memberRepository.findByGroupIdAndUserId(groupId, invited.getId()).isPresent())
            throw new IllegalArgumentException("Użytkownik jest już w grupie");

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(invited)
                .status(GroupMember.MemberStatus.INVITED)
                .build();
        GroupMember saved = memberRepository.save(member);
        notificationService.notify(invited.getId(), NotificationType.GROUP_INVITATION,
                "Zostałeś zaproszony do grupy \"" + group.getName() + "\" przez " + group.getOwner().getName(),
                "/groups/" + groupId);
        return saved;
    }

    @Transactional
    public GroupMember respondToInvite(Long groupId, Long userId, boolean accept) {
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono zaproszenia"));
        if (member.getStatus() != GroupMember.MemberStatus.INVITED)
            throw new IllegalArgumentException("Zaproszenie zostało już rozpatrzone");
        member.setStatus(accept ? GroupMember.MemberStatus.ACCEPTED : GroupMember.MemberStatus.DECLINED);
        GroupMember saved = memberRepository.save(member);
        Group group = member.getGroup();
        NotificationType type = accept ? NotificationType.GROUP_JOIN_APPROVED : NotificationType.GROUP_JOIN_DECLINED;
        String msg = accept
                ? "Twoja prośba o dołączenie do grupy \"" + group.getName() + "\" została zaakceptowana"
                : "Twoja prośba o dołączenie do grupy \"" + group.getName() + "\" została odrzucona";
        notificationService.notify(member.getUser().getId(), type, msg, "/groups/" + group.getId());
        return saved;
    }

    @Transactional
    public GroupMember approveMember(Long groupId, Long targetUserId, Long ownerId) {
        findAndCheckOwner(groupId, ownerId);
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie jest w grupie"));
        if (member.getStatus() != GroupMember.MemberStatus.PENDING)
            throw new IllegalArgumentException("Tylko oczekujące prośby można zatwierdzić");
        member.setStatus(GroupMember.MemberStatus.ACCEPTED);
        GroupMember saved = memberRepository.save(member);
        notificationService.notify(targetUserId, NotificationType.GROUP_JOIN_APPROVED,
                "Twoja prośba o dołączenie do grupy \"" + member.getGroup().getName() + "\" została zaakceptowana",
                "/groups/" + groupId);
        return saved;
    }

    @Transactional
    public void removeMember(Long groupId, Long targetUserId, Long requesterId) {
        Group group = findById(groupId);
        boolean isOwner = group.getOwner().getId().equals(requesterId);
        boolean isSelf  = requesterId.equals(targetUserId);
        if (!isOwner && !isSelf)
            throw new ExpeditionService.AccessDeniedException("Brak uprawnień");
        if (isOwner && group.getOwner().getId().equals(targetUserId))
            throw new IllegalArgumentException("Właściciel nie może opuścić własnej grupy");
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie jest członkiem grupy"));
        memberRepository.delete(member);
    }

    @Transactional
    public GroupMessage sendMessage(Long groupId, String content, Long userId) {
        Group group = findById(groupId);
        String role = resolveViewerRole(group, userId);
        if (!"OWNER".equals(role) && !"MEMBER".equals(role))
            throw new ExpeditionService.AccessDeniedException("Tylko członkowie grupy mogą pisać na czacie");
        User author = findUser(userId);
        GroupMessage msg = GroupMessage.builder()
                .group(group)
                .author(author)
                .content(content)
                .build();
        return messageRepository.save(msg);
    }

    @Transactional(readOnly = true)
    public List<GroupMessage> getMessages(Long groupId, Long userId) {
        Group group = findById(groupId);
        String role = resolveViewerRole(group, userId);
        if (!"OWNER".equals(role) && !"MEMBER".equals(role))
            throw new ExpeditionService.AccessDeniedException("Brak dostępu do czatu");
        return messageRepository.findTop100ByGroupIdOrderByCreatedAtAsc(groupId);
    }

    private Group findAndCheckOwner(Long groupId, Long userId) {
        Group group = findById(groupId);
        if (!group.getOwner().getId().equals(userId))
            throw new ExpeditionService.AccessDeniedException("Tylko właściciel może wykonać tę akcję");
        return group;
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika id=" + id));
    }
}
