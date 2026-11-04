package dev.mlua.mcapi.entity;

public interface MCEntityType {
    String getDescriptionId();

    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private String descriptionId;

        public Builder descriptionId(String descriptionId) {
            this.descriptionId = descriptionId;
            return this;
        }

        public MCEntityType build() {
            return () -> descriptionId;
        }
    }
}
