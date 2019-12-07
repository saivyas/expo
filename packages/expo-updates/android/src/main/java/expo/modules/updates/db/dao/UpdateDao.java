package expo.modules.updates.db.dao;

import expo.modules.updates.UpdateStatus;
import expo.modules.updates.db.entity.UpdateEntity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

@Dao
public abstract class UpdateDao {
  /**
   * for private use only
   * must be marked public for Room
   * so we use the underscore to discourage use
   */
  @Query("SELECT * FROM updates WHERE status IN (:statuses);")
  public abstract List<UpdateEntity> _loadUpdatesWithStatuses(List<UpdateStatus> statuses);

  @Query("SELECT * FROM updates WHERE id = :id;")
  public abstract List<UpdateEntity> _loadUpdatesWithId(UUID id);

  @Query("UPDATE updates SET keep = 1 WHERE id = :id;")
  public abstract void _keepUpdate(UUID id);

  @Query("UPDATE updates SET status = :status WHERE id = :id;")
  public abstract void _markUpdateWithStatus(UpdateStatus status, UUID id);

  @Query("UPDATE updates SET keep = 0, status = :status WHERE commit_time < (SELECT commit_time FROM updates WHERE id = :id);")
  public abstract void _markOlderUpdatesForDeletion(UpdateStatus status, UUID id);

  @Query("DELETE FROM updates_assets WHERE update_id IN (SELECT id FROM updates WHERE keep = 0);")
  public abstract void _deleteUnusedUpdatesAssets();

  @Query("DELETE FROM updates WHERE keep = 0;")
  public abstract void _deleteUnusedUpdateEntities();


  /**
   * for public use
   */
  public List<UpdateEntity> loadLaunchableUpdates() {
    return _loadUpdatesWithStatuses(Arrays.asList(UpdateStatus.LAUNCHABLE, UpdateStatus.READY));
  }

  public UpdateEntity loadUpdateWithId(UUID id) {
    List<UpdateEntity> updateEntities = _loadUpdatesWithId(id);
    return updateEntities.size() > 0 ? updateEntities.get(0) : null;
  }

  @Query("SELECT relative_path FROM updates INNER JOIN assets ON updates.launch_asset_id = assets.id WHERE updates.id = :id;")
  public abstract String loadLaunchAssetUrl(UUID id);

  @Insert
  public abstract void insertUpdate(UpdateEntity update);

  public void markUpdateReady(UpdateEntity update) {
    _markUpdateWithStatus(UpdateStatus.READY, update.id);
  }

  @Transaction
  public void markUpdatesForDeletion(UpdateEntity launchedUpdate) {
    _keepUpdate(launchedUpdate.id);
    _markOlderUpdatesForDeletion(UpdateStatus.UNUSED, launchedUpdate.id);
  }

  @Transaction
  public void deleteUnusedUpdates() {
    _deleteUnusedUpdatesAssets();
    _deleteUnusedUpdateEntities();
  }
}
