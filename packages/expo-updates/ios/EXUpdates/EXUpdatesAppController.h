//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAppLauncher.h>
#import <EXUpdates/EXUpdatesAppLoader.h>
#import <EXUpdates/EXUpdatesDatabase.h>
#import <EXUpdates/EXUpdatesSelectionPolicy.h>
#import <React/RCTBridge.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesAppController : NSObject <EXUpdatesAppLoaderDelegate>

@property (nonatomic, readonly) EXUpdatesAppLauncher *launcher;
@property (nonatomic, readonly) EXUpdatesDatabase *database;
@property (nonatomic, readonly) EXUpdatesSelectionPolicy *selectionPolicy;
@property (nonatomic, readwrite, weak) RCTBridge *bridge;

@property (nonatomic, readonly, assign) BOOL isEnabled;

+ (instancetype)sharedInstance;

- (void)start;
- (BOOL)reloadBridge;
- (NSURL * _Nullable)launchAssetUrl;
- (NSURL *)updatesDirectory;

- (void)handleErrorWithDomain:(NSString *)errorDomain
                  description:(NSString *)description
                         info:(NSDictionary * _Nullable)info
                      isFatal:(BOOL)isFatal;

@end

NS_ASSUME_NONNULL_END
