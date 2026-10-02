package edu.cit.escuzar.channel;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChannelOrderRepository extends JpaRepository<ChannelOrder, String> {
    boolean existsByStatusStartingWith(String prefix);
    boolean existsByStatus(String status);
}
