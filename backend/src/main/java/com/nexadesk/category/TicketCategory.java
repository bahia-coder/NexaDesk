package com.nexadesk.category;

import com.nexadesk.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ticket_categories")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketCategory extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, length = 40)
    private String icon;

    /** Comma-separated words used by the heuristic classifier (AI module). */
    @Column(length = 1000)
    private String keywords;

    @Column(nullable = false)
    private boolean active = true;

    /** Composition: subcategories added to a new category are persisted with it. */
    @OneToMany(mappedBy = "category", cascade = CascadeType.PERSIST)
    @OrderBy("name ASC")
    private List<TicketSubCategory> subcategories = new ArrayList<>();

    public TicketCategory(String name, String description, String icon, String keywords) {
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.keywords = keywords;
    }

    public TicketSubCategory addSubcategory(String subcategoryName) {
        TicketSubCategory subcategory = new TicketSubCategory(this, subcategoryName);
        subcategories.add(subcategory);
        return subcategory;
    }
}
