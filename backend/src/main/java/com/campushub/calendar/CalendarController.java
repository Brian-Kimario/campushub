package com.campushub.calendar;

import com.campushub.identity.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Academic calendar endpoint (API_CONTRACT §9). */
@RestController
@RequestMapping("/calendar")
public class CalendarController {

    private final CalendarService calendar;

    public CalendarController(CalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping
    public List<CalendarEntry> entries(@AuthenticationPrincipal AuthenticatedUser caller,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return calendar.entries(caller, from, to);
    }
}
