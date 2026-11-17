package com.example.StreamSphere.controller;

import com.example.StreamSphere.model.Song;
import com.example.StreamSphere.repository.SongRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/songs")
@CrossOrigin(origins = "*")
public class songController {

    @Autowired
    private SongRepository songRepository;

    @GetMapping
    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }
}
