//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAsset.h>
#import <EXUpdates/EXUpdatesUpdate.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, EXUpdatesDatabaseHashType) {
  EXUpdatesDatabaseHashTypeSha1 = 0
};

@interface EXUpdatesDatabase : NSObject

@property (nonatomic, readonly) NSLock *lock;

- (BOOL)openDatabaseWithError:(NSError **)error;
- (void)closeDatabase;

- (void)addUpdate:(EXUpdatesUpdate *)update;
- (void)addNewAssets:(NSArray<EXUpdatesAsset *>*)assets toUpdateWithId:(NSUUID *)updateId;
- (BOOL)addExistingAsset:(EXUpdatesAsset *)asset toUpdateWithId:(NSUUID *)updateId;
- (void)markUpdateReadyWithId:(NSUUID *)updateId;

- (void)markUpdatesForDeletion;
- (NSArray<NSDictionary *>*)markAssetsForDeletion;
- (void)deleteAssetsWithIds:(NSArray<NSNumber *>*)assetIds;
- (void)deleteUnusedUpdates;

- (NSArray<EXUpdatesUpdate *>*)launchableUpdates;
- (EXUpdatesUpdate * _Nullable)updateWithId:(NSUUID *)updateId;
- (NSURL * _Nullable)launchAssetUrlWithUpdateId:(NSUUID *)updateId;
- (NSArray<NSDictionary *>*)assetsForUpdateId:(NSUUID *)updateId;

@end

NS_ASSUME_NONNULL_END
