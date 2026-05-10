package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "expedition_equipment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expedition_id", "item"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ExpeditionEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expedition_id", nullable = false)
    @ToString.Exclude
    private Expedition expedition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentItem item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequirementLevel level;

    public enum EquipmentItem {
        RACZKI,
        RAKI,
        CZEKAN,
        KIJKI_TREKKINGOWE,
        STUPTUTY,
        KASK,
        LATARKA_CZOLOWA,
        KREM_Z_FILTREM,
        OKULARY_PRZECIWSLONECZNE
    }

    public enum RequirementLevel {
        REQUIRED,
        RECOMMENDED
    }
}
