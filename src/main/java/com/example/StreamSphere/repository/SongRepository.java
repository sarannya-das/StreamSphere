package com.example.StreamSphere.repository;
import com.example.StreamSphere.model.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SongRepository extends JpaRepository<Song, Long> {
}