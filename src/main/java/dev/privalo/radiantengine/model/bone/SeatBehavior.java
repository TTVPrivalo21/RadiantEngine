package dev.privalo.radiantengine.model.bone;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import dev.privalo.radiantengine.util.EntityCompat;

import java.util.Collection;
import java.util.List;

/**
 * Seat on a p_ bone (passenger) or on the "mount" bone (driver). The rider sits on an empty ItemDisplay that
 * follows the bone every tick.
 */
public final class SeatBehavior implements BoneBehavior {

    private final RigBone bone;
    private ItemDisplay seat;
    private Location lastLocation;

    public SeatBehavior(RigBone bone) {
        this.bone = bone;
    }

    public boolean isDriver() {
        return bone.getBlueprint().isDriverSeat();
    }

    public RigBone getBone() {
        return bone;
    }

    private Location target(Location origin) {
        return bone.absoluteLocation(origin).add(0, bone.getModel().getPlugin().getSettings().seatOffsetY, 0);
    }

    @Override
    public void spawn(Location origin) {
        lastLocation = target(origin);
        seat = PartFactory.itemDisplay(bone.getModel(), lastLocation, d -> d.setItemStack(null));
    }

    @Override
    public void update(Location origin, boolean originMoved) {
        if (seat == null || !seat.isValid()) {
            return;
        }
        Location loc = target(origin);
        if (lastLocation == null || lastLocation.distanceSquared(loc) > 1.0E-6) {
            EntityCompat.teleportWithPassengers(seat, PartFactory.flat(loc));
            lastLocation = loc;
        }
    }

    public Entity getRider() {
        if (seat == null) {
            return null;
        }
        List<Entity> passengers = seat.getPassengers();
        return passengers.isEmpty() ? null : passengers.get(0);
    }

    public boolean isOccupied() {
        return getRider() != null;
    }

    public boolean mount(Entity rider) {
        if (seat == null || !seat.isValid() || isOccupied() || rider.equals(bone.getModel().getOwner().getBase())) {
            return false;
        }
        if (rider.isInsideVehicle()) {
            rider.leaveVehicle();
        }
        return seat.addPassenger(rider);
    }

    public void dismount() {
        if (seat != null) {
            seat.eject();
        }
    }

    public ItemDisplay getSeatEntity() {
        return seat;
    }

    @Override
    public void destroy() {
        if (seat != null) {
            seat.eject();
            bone.getModel().unregisterPart(seat);
            seat.remove();
            seat = null;
        }
    }

    @Override
    public Collection<? extends Entity> entities() {
        return seat == null ? List.of() : List.of(seat);
    }
}
