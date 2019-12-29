//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAsset.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesManifest : NSObject

@property (nonatomic, strong, readonly) NSUUID *updateId;
@property (nonatomic, strong, readonly) NSDate *commitTime;
@property (nonatomic, strong, readonly) NSString *binaryVersions;
@property (nonatomic, strong, readonly) NSDictionary *metadata;
@property (nonatomic, strong, readonly) NSURL *bundleUrl;
@property (nonatomic, strong, readonly) NSArray<EXUpdatesAsset *>*assets;

@property (nonatomic, strong, readonly) NSDictionary *rawManifest;

+ (instancetype)manifestWithBareManifest:(NSDictionary *)bareManifest;
+ (instancetype)manifestWithManagedManifest:(NSDictionary *)managedManifest;

@end

NS_ASSUME_NONNULL_END
