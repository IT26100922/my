package com.moviebooking.theatre.service;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.theatre.dto.TheatreDto.TheatreRequest;
import com.moviebooking.theatre.dto.TheatreDto.TheatreResponse;
import com.moviebooking.theatre.entity.Theatre;
import com.moviebooking.theatre.repository.ScreenRepository;
import com.moviebooking.theatre.repository.TheatreRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for theatres.
 * CREATE: create()   READ: list() / get()   UPDATE: update()   DELETE: delete()
 */
@Service
@Transactional
public class TheatreService {

    private final TheatreRepository theatres;
    private final ScreenRepository screens;

    public TheatreService(TheatreRepository theatres, ScreenRepository screens) {
        this.theatres = theatres;
        this.screens = screens;
    }

    @Transactional(readOnly = true)
    public List<TheatreResponse> list() {
        return theatres.findAll(Sort.by("name")).stream().map(TheatreResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TheatreResponse get(Long id) {
        return TheatreResponse.from(find(id));
    }

    public TheatreResponse create(TheatreRequest req) {
        Theatre theatre = new Theatre();
        apply(theatre, req);
        return TheatreResponse.from(theatres.save(theatre));
    }

    public TheatreResponse update(Long id, TheatreRequest req) {
        Theatre theatre = find(id);
        apply(theatre, req);
        return TheatreResponse.from(theatre);
    }

    /** Blocked while screens still belong to the theatre. */
    public void delete(Long id) {
        Theatre theatre = find(id);
        if (screens.existsByTheatreId(id)) {
            throw new BusinessException("This theatre still has screens. Delete its screens first.");
        }
        theatres.delete(theatre);
    }

    /** Used by ScreenService to attach screens to a theatre. */
    @Transactional(readOnly = true)
    public Theatre findEntity(Long id) {
        return find(id);
    }

    private Theatre find(Long id) {
        return theatres.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre not found: " + id));
    }

    private void apply(Theatre theatre, TheatreRequest req) {
        theatre.setName(req.name().trim());
        theatre.setCity(req.city().trim());
        theatre.setAddress(req.address() == null || req.address().isBlank() ? null : req.address().trim());
        theatre.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
    }
}
