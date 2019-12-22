package expo.modules.updates;

import expo.modules.updates.db.entity.UpdateEntity;

import java.util.List;

/**
 * Simple Update selection policy which chooses
 * the newest update (based on commit time) out
 * of all the possible stored updates.
 *
 * If multiple updates have the same (most
 * recent) commit time, this class will return
 * the earliest one in the list.
 */
public class SelectionPolicyNewest implements SelectionPolicy {

  @Override
  public UpdateEntity selectUpdateToLaunch(List<UpdateEntity> updates) {
    UpdateEntity updateToLaunch = null;
    for (UpdateEntity update : updates) {
      if (updateToLaunch == null || updateToLaunch.commitTime.before(update.commitTime)) {
        updateToLaunch = update;
      }
    }
    return updateToLaunch;
  }

  @Override
  public boolean shouldLoadNewUpdate(UpdateEntity newUpdate, UpdateEntity launchedUpdate) {
    return newUpdate.commitTime.after(launchedUpdate.commitTime);
  }
}
