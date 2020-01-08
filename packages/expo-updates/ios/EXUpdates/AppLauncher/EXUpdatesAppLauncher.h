//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesUpdate.h>
#import <EXUpdates/EXUpdatesSelectionPolicy.h>

NS_ASSUME_NONNULL_BEGIN

@class EXUpdatesAppLauncher;

@protocol EXUpdatesAppLauncherDelegate <NSObject>

- (void)appLauncher:(EXUpdatesAppLauncher *)appLauncher didFinishWithSuccess:(BOOL)success;

@end

@interface EXUpdatesAppLauncher : NSObject

@property (nonatomic, weak) id<EXUpdatesAppLauncherDelegate> delegate;
@property (nonatomic, strong, readonly) EXUpdatesUpdate * _Nullable launchedUpdate;
@property (nonatomic, strong, readonly) NSURL * _Nullable launchAssetUrl;
@property (nonatomic, strong, readonly) NSDictionary * _Nullable assetFilesMap;

- (void)launchUpdateWithSelectionPolicy:(EXUpdatesSelectionPolicy *)selectionPolicy;

- (EXUpdatesUpdate * _Nullable)launchableUpdateWithSelectionPolicy:(EXUpdatesSelectionPolicy *)selectionPolicy;
- (NSUUID * _Nullable)launchedUpdateId;

@end

NS_ASSUME_NONNULL_END
