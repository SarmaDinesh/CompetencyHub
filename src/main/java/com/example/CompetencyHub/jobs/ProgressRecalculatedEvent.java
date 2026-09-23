package com.example.CompetencyHub.jobs;

import java.time.Instant;

/** In-process event. A plain record -- no base class needed since Spring 4.2; the slides'
 *  ApplicationEvent inheritance is no longer required. */
public record ProgressRecalculatedEvent(int examined, int updated, Instant startedAt) { }