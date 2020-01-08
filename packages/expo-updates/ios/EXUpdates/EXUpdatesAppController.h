//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAppLauncher.h>
#import <EXUpdates/EXUpdatesAppLoader.h>
#import <EXUpdates/EXUpdatesAppLoaderEmbedded.h>
#import <EXUpdates/EXUpdatesDatabase.h>
#import <EXUpdates/EXUpdatesSelectionPolicy.h>
#import <React/RCTBridge.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesAppController : NSObject <EXUpdatesAppLoaderDelegate, EXUpdatesAppLauncherDelegate>

@property (nonatomic, readonly) EXUpdatesAppLauncher *launcher;
@property (nonatomic, readonly) EXUpdatesDatabase *database;
@property (nonatomic, readonly) EXUpdatesSelectionPolicy *selectionPolicy;
@property (nonatomic, readonly) EXUpdatesAppLoaderEmbedded *embeddedAppLoader;
@property (nonatomic, readwrite, weak) RCTBridge *bridge;

@property (nonatomic, readonly) NSURL *updatesDirectory;
@property (nonatomic, readonly, assign) BOOL isEnabled;

+ (instancetype)sharedInstance;

- (void)start;
- (BOOL)reloadBridge;
- (NSURL * _Nullable)launchAssetUrl;

@end

NS_ASSUME_NONNULL_END
