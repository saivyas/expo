//  Copyright © 2019 650 Industries. All rights reserved.

#import <UIKit/UIKit.h>

#import <EXUpdates/EXUpdatesConfig.h>
#import <EXUpdates/EXUpdatesAppController.h>
#import <EXUpdates/EXUpdatesAppLoaderEmbedded.h>
#import <EXUpdates/EXUpdatesAppLoaderRemote.h>
#import <EXUpdates/EXUpdatesSelectionPolicyNewest.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesAppController ()

@property (nonatomic, readwrite, strong) EXUpdatesAppLauncher *launcher;
@property (nonatomic, readwrite, strong) EXUpdatesDatabase *database;
@property (nonatomic, readwrite, strong) EXUpdatesSelectionPolicy *selectionPolicy;

@property (nonatomic, readonly, strong) EXUpdatesAppLoaderRemote *remoteAppLoader;

@property (nonatomic, strong) NSURL *updatesDirectory;

@property (nonatomic, readwrite, assign) BOOL isEnabled;

@end

@implementation EXUpdatesAppController

+ (instancetype)sharedInstance
{
  static EXUpdatesAppController *theController;
  static dispatch_once_t once;
  dispatch_once(&once, ^{
    if (!theController) {
      theController = [[EXUpdatesAppController alloc] init];
    }
  });
  return theController;
}

- (instancetype)init
{
  if (self = [super init]) {
    _launcher = [[EXUpdatesAppLauncher alloc] init];
    _database = [[EXUpdatesDatabase alloc] init];
    _selectionPolicy = [[EXUpdatesSelectionPolicy alloc] init];
    _isEnabled = NO;
  }
  return self;
}

- (void)start
{
  _isEnabled = YES;
  [_database openDatabase];
  [self _copyEmbeddedAssets];
  [_launcher launchUpdateWithSelectionPolicy:_selectionPolicy];

  _remoteAppLoader = [[EXUpdatesAppLoaderRemote alloc] init];
  _remoteAppLoader.delegate = self;
  [_remoteAppLoader loadUpdateFromUrl:[EXUpdatesConfig sharedInstance].remoteUrl];
}

- (BOOL)reloadBridge
{
  if (_bridge) {
    [_bridge reload];
    return true;
  } else {
    NSLog(@"EXUpdatesAppController: Failed to reload because bridge was nil. Did you set the bridge property on the controller singleton?");
    return false;
  }
}

- (NSURL * _Nullable)launchAssetUrl
{
  NSUUID *launchedUpdateId = [_launcher launchedUpdateId];
  if (launchedUpdateId) {
    return [_database launchAssetUrlWithUpdateId:[_launcher launchedUpdateId]];
  } else {
    return nil;
  }
}

- (NSURL *)updatesDirectory
{
  if (!_updatesDirectory) {
    NSFileManager *fileManager = NSFileManager.defaultManager;
    NSURL *applicationDocumentsDirectory = [[fileManager URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask] lastObject];
    _updatesDirectory = [applicationDocumentsDirectory URLByAppendingPathComponent:@".expo-internal"];
    NSString *updatesDirectoryPath = [_updatesDirectory path];

    BOOL isDir;
    BOOL exists = [fileManager fileExistsAtPath:updatesDirectoryPath isDirectory:&isDir];
    if (!exists || !isDir) {
      if (!isDir) {
        NSError *err;
        BOOL wasRemoved = [fileManager removeItemAtPath:updatesDirectoryPath error:&err];
        if (!wasRemoved) {
          // TODO: handle error
        }
      }
      NSError *err;
      BOOL wasCreated = [fileManager createDirectoryAtPath:updatesDirectoryPath withIntermediateDirectories:YES attributes:nil error:&err];
      if (!wasCreated) {
        // TODO: handle error
      }
    }
  }
  return _updatesDirectory;
}

- (void)handleErrorWithDomain:(NSString *)errorDomain
                  description:(NSString *)description
                         info:(NSDictionary * _Nullable)info
                      isFatal:(BOOL)isFatal
{
  // do something!!!!
  NSLog(@"EXUpdates error: %@", description);
  NSLog(@"%@", [NSThread callStackSymbols]);
}

# pragma mark - internal

- (void)_copyEmbeddedAssets
{
  EXUpdatesAppLoaderEmbedded *embeddedAppLoader = [[EXUpdatesAppLoaderEmbedded alloc] init];
  [embeddedAppLoader loadUpdateFromEmbeddedManifest];
}

# pragma mark - EXUpdatesAppLoaderDelegate

- (BOOL)appLoader:(EXUpdatesAppLoader *)appLoader shouldStartLoadingUpdate:(EXUpdatesUpdate *)update
{
  BOOL shouldStartLoadingUpdate = [_selectionPolicy shouldLoadNewUpdate:update withLaunchedUpdate:_launcher.launchedUpdate];
  NSLog(@"manifest downloaded, shouldStartLoadingUpdate is %@", shouldStartLoadingUpdate ? @"YES" : @"NO");
  return shouldStartLoadingUpdate;
}

- (void)appLoader:(EXUpdatesAppLoader *)appLoader didFinishLoadingUpdate:(EXUpdatesUpdate * _Nullable)update
{
  // maybe do something?
  NSLog(@"update with UUID %@ finished loading", [update.updateId UUIDString]);
}

- (void)appLoader:(EXUpdatesAppLoader *)appLoader didFailWithError:(NSError *)error
{
  // probably do something
  NSLog(@"update failed to load: %@", [error localizedDescription]);
}

@end

NS_ASSUME_NONNULL_END
