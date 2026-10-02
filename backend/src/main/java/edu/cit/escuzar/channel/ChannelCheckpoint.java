package edu.cit.escuzar.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tiangge_checkpoint")
class ChannelCheckpoint {
    @Id private int id;
    @Column(nullable = false) private long cursor;
    protected ChannelCheckpoint() {}
    ChannelCheckpoint(int id, long cursor) { this.id = id; this.cursor = cursor; }
    long getCursor() { return cursor; }
    void setCursor(long cursor) { this.cursor = cursor; }
}
