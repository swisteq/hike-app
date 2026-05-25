package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expedition_transport_sections",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expedition_id", "section_type", "expedition_day_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ExpeditionTransportSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expedition_id", nullable = false)
    @ToString.Exclude
    private Expedition expedition;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type", nullable = false)
    private SectionType sectionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expedition_day_id")
    @ToString.Exclude
    private ExpeditionDay expeditionDay;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    @ToString.Exclude
    private List<ExpeditionTransportOption> options = new ArrayList<>();

    public enum SectionType {
        ARRIVAL, RETURN, DAY_TRANSITION
    }
}
