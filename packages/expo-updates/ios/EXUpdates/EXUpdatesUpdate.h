//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAsset.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, EXUpdatesUpdateStatus) {
  EXUpdatesUpdateStatusFailed = 0,
  EXUpdatesUpdateStatusReady = 1,
  EXUpdatesUpdateStatusLaunchable = 2,
  EXUpdatesUpdateStatusPending = 3,
  EXUpdatesUpdateStatusUnused = 4
};

@interface EXUpdatesUpdate : NSObject

@property (nonatomic, strong, readonly) NSUUID *updateId;
@property (nonatomic, strong, readonly) NSDate *commitTime;
@property (nonatomic, strong, readonly) NSString *binaryVersions;
@property (nonatomic, strong, readonly) NSDictionary * _Nullable metadata;
@property (nonatomic, assign, readonly) EXUpdatesUpdateStatus status;
@property (nonatomic, assign, readonly) BOOL keep;
@property (nonatomic, strong, readonly) NSURL *bundleUrl;
@property (nonatomic, strong, readonly) NSArray<EXUpdatesAsset *>*assets;

@property (nonatomic, strong, readonly) NSDictionary *rawManifest;

+ (instancetype)updateWithId:(NSUUID *)updateId
                  commitTime:(NSDate *)commitTime
              binaryVersions:(NSString *)binaryVersions
                    metadata:(NSDictionary * _Nullable)metadata
                      status:(EXUpdatesUpdateStatus)status
                        keep:(BOOL)keep;

+ (instancetype)updateWithBareManifest:(NSDictionary *)bareManifest;
+ (instancetype)updateWithManagedManifest:(NSDictionary *)managedManifest;

@end

NS_ASSUME_NONNULL_END
