package com.chominjungum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "assignment_item")
@Getter
@Setter
@NoArgsConstructor
public class AssignmentItem {

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {

        @Column(name = "assignment_id")
        private UUID assignmentId;

        @Column(name = "item_id")
        private UUID itemId;

        public Key(UUID assignmentId, UUID itemId) {
            this.assignmentId = assignmentId;
            this.itemId = itemId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return Objects.equals(assignmentId, key.assignmentId) && Objects.equals(itemId, key.itemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(assignmentId, itemId);
        }
    }

    @EmbeddedId
    private Key id;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    public AssignmentItem(UUID assignmentId, UUID itemId, int orderNo) {
        this.id = new Key(assignmentId, itemId);
        this.orderNo = orderNo;
    }
}
