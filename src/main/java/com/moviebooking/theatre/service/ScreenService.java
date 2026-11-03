package com.moviebooking.theatre.service;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.show.repository.ShowtimeRepository;
import com.moviebooking.theatre.dto.ScreenDto.ScreenRequest;
import com.moviebooking.theatre.dto.ScreenDto.ScreenResponse;
import com.moviebooking.theatre.entity.Screen;
import com.moviebooking.theatre.repository.ScreenRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for screens.
 * CREATE: create()   READ: list() / get()   UPDATE: update()   DELETE: delete()
 */
@Service
@Transactional
public class ScreenService {

    private final ScreenRepository screens;
    private final ScreenFactory screenFactory;
    private final TheatreService theatreService;
    private final ShowtimeRepository showtimes;

    public ScreenService(ScreenRepository screens, ScreenFactory screenFactory,
                         TheatreService theatreService, ShowtimeRepository showtimes) {
        this.screens = screens;
        this.screenFactory = screenFactory;
        this.theatreService = theatreService;
        this.showtimes = showtimes;
    }

    /** READ: all screens, or only those of one theatre. */
    @Transactional(readOnly = true)
    public List<ScreenResponse> list(Long theatreId) {
        List<Screen> found = theatreId == null
                ? screens.findAll(Sort.by("name"))
                : screens.findByTheatreIdOrderByNameAsc(theatreId);
        return found.stream().map(ScreenResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ScreenResponse get(Long id) {
        return ScreenResponse.from(find(id));
    }

    /** CREATE: the factory picks the subclass (STANDARD / PREMIUM / VIP). */
    public ScreenResponse create(ScreenRequest req) {
        Screen screen = screenFactory.create(req.screenType());
        screen.setName(req.name().trim());
        screen.setTheatre(theatreService.findEntity(req.theatreId()));
        screen.setLayout(req.rowCount(), req.seatsPerRow());
        return ScreenResponse.from(screens.save(screen));
    }

    /** UPDATE: the screen type is fixed; the layout cannot change once showtimes exist. */
    public ScreenResponse update(Long id, ScreenRequest req) {
        Screen screen = find(id);
        if (!screen.getScreenType().equalsIgnoreCase(req.screenType().trim())) {
            throw new BusinessException("Screen type cannot be changed. Create a new screen instead.");
        }
        boolean layoutChanged = screen.getRowCount() != req.rowCount() || screen.getSeatsPerRow() != req.seatsPerRow();
        if (layoutChanged && showtimes.existsByScreenId(id)) {
            throw new BusinessException("Seat layout cannot change while this screen has showtimes");
        }
        screen.setName(req.name().trim());
        screen.setTheatre(theatreService.findEntity(req.theatreId()));
        screen.setLayout(req.rowCount(), req.seatsPerRow());
        return ScreenResponse.from(screen);
    }

    /** DELETE: blocked while showtimes use the screen. */
    public void delete(Long id) {
        Screen screen = find(id);
        if (showtimes.existsByScreenId(id)) {
            throw new BusinessException("This screen has showtimes. Delete those first.");
        }
        screens.delete(screen);
    }

    private Screen find(Long id) {
        return screens.findById(id).orElseThrow(() -> new ResourceNotFoundException("Screen not found: " + id));
    }
}
