package com.extantape.waystation;

import java.util.Optional;
import org.bukkit.Location;

/** Test stand-in with the same shape as Waystation 0.4.0's service. */
public interface LandingService {
    Optional<Location> landing(String world);
}
