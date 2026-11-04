package org.example.bloggingplatformi.model;

/** A row of the tags table. */
public record Tag(long id, String name) {

    /** What a ComboBox shows for this tag. */
    @Override
    public String toString() {
        return name;
    }
}
