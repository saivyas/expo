//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesConfig.h>
#import <EXUpdates/EXUpdatesAppController.h>
#import <EXUpdates/EXUpdatesAppLoaderEmbedded.h>
#import <EXUpdates/EXUpdatesAppLoaderRemote.h>
#import <EXUpdates/EXUpdatesSelectionPolicyNewest.h>
#import <SystemConfiguration/SystemConfiguration.h>
#import <arpa/inet.h>

NS_ASSUME_NONNULL_BEGIN

static NSString * const kEXUpdatesEventName = @"Expo.nativeUpdatesEvent";
static NSString * const kEXUpdatesUpdateAvailableEventName = @"updateAvailable";
static NSString * const kEXUpdatesNoUpdateAvailableEventName = @"noUpdateAvailable";
static NSString * const kEXUpdatesErrorEventName = @"error";

@interface EXUpdatesAppController ()

@property (nonatomic, readwrite, strong) EXUpdatesAppLauncher *launcher;
@property (nonatomic, readwrite, strong) EXUpdatesDatabase *database;
@property (nonatomic, readwrite, strong) EXUpdatesSelectionPolicy *selectionPolicy;
@property (nonatomic, readwrite, strong) EXUpdatesAppLoaderEmbedded *embeddedAppLoader;
@property (nonatomic, readwrite, strong) EXUpdatesAppLoaderRemote *remoteAppLoader;

@property (nonatomic, readwrite, strong) NSURL *updatesDirectory;
@property (nonatomic, readwrite, assign) BOOL isEnabled;

@property (nonatomic, strong) NSTimer *timer;
@property (nonatomic, strong) NSCondition *launchCondition;
@property (nonatomic, assign) BOOL isReadyToLaunch;
@property (nonatomic, assign) BOOL isTimeoutFinished;
@property (nonatomic, assign) BOOL hasLaunched;

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
    _remoteAppLoader = [[EXUpdatesAppLoaderRemote alloc] init];
    _embeddedAppLoader = [[EXUpdatesAppLoaderEmbedded alloc] init];
    _isEnabled = NO;
    _isReadyToLaunch = NO;
    _isTimeoutFinished = NO;
    _hasLaunched = NO;
  }
  return self;
}

- (void)start
{
  _isEnabled = YES;
  [_database openDatabaseWithError:nil];
  _launchCondition = [[NSCondition alloc] init];

  NSNumber *launchWaitMs = [EXUpdatesConfig sharedInstance].launchWaitMs;
  if ([launchWaitMs isEqualToNumber:@(0)]) {
    _isTimeoutFinished = YES;
  } else {
    NSDate *fireDate = [NSDate dateWithTimeIntervalSinceNow:[launchWaitMs doubleValue] / 1000];
    _timer = [[NSTimer alloc] initWithFireDate:fireDate interval:0 target:self selector:@selector(_timerDidFire) userInfo:nil repeats:NO];
    [[NSRunLoop currentRunLoop] addTimer:_timer forMode:NSDefaultRunLoopMode];
  }

  [self _copyEmbeddedAssets];

  _launcher.delegate = self;
  [_launcher launchUpdateWithSelectionPolicy:_selectionPolicy];
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
  while (!_isReadyToLaunch || !_isTimeoutFinished) {
    [_launchCondition wait];
  }
  _hasLaunched = YES;
  return _launcher.launchAssetUrl ?: nil;
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

# pragma mark - internal

- (void)_timerDidFire
{
  _isTimeoutFinished = YES;
  [_launchCondition signal];
}

- (void)_copyEmbeddedAssets
{
  if ([_selectionPolicy shouldLoadNewUpdate:_embeddedAppLoader.embeddedManifest withLaunchedUpdate:[_launcher launchableUpdateWithSelectionPolicy:_selectionPolicy]]) {
    [_embeddedAppLoader loadUpdateFromEmbeddedManifest];
  }
}

- (void)_sendEventToBridgeWithType:(NSString *)eventType body:(NSDictionary *)body
{
  if (_bridge) {
    NSMutableDictionary *mutableBody = [body mutableCopy];
    mutableBody[@"type"] = eventType;
    [_bridge enqueueJSCall:@"RCTDeviceEventEmitter.emit" args:@[kEXUpdatesEventName, mutableBody]];
  } else {
    NSLog(@"EXUpdatesAppController: Could not emit %@ event. Did you set the bridge property on the controller singleton?", eventType);
  }
}

- (BOOL)_shouldCheckForUpdate
{
  if (_hasLaunched) {
    return NO;
  }

  EXUpdatesConfig *config = [EXUpdatesConfig sharedInstance];
  switch (config.checkOnLaunch) {
    case EXUpdatesCheckAutomaticallyConfigNever:
      return NO;
    case EXUpdatesCheckAutomaticallyConfigWifiOnly: {
      struct sockaddr_in zeroAddress;
      bzero(&zeroAddress, sizeof(zeroAddress));
      zeroAddress.sin_len = sizeof(zeroAddress);
      zeroAddress.sin_family = AF_INET;

      SCNetworkReachabilityRef reachability = SCNetworkReachabilityCreateWithAddress(kCFAllocatorDefault, (const struct sockaddr *) &zeroAddress);
      SCNetworkReachabilityFlags flags;
      SCNetworkReachabilityGetFlags(reachability, &flags);

      return (flags & kSCNetworkReachabilityFlagsIsWWAN) == 0;
    }
    case EXUpdatesCheckAutomaticallyConfigAlways:
    default:
      return YES;
  }
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
  if (update) {
    if (!_hasLaunched) {
      if (_timer) {
        [_timer invalidate];
      }
      _isTimeoutFinished = YES;
      EXUpdatesAppLauncher *newLauncher = [[EXUpdatesAppLauncher alloc] init];
      [newLauncher launchUpdateWithSelectionPolicy:_selectionPolicy];
    } else {
      [self _sendEventToBridgeWithType:kEXUpdatesUpdateAvailableEventName
                                  body:@{@"manifest": update.rawManifest}];
    }
  } else {
    NSLog(@"No update available");
    [self _sendEventToBridgeWithType:kEXUpdatesNoUpdateAvailableEventName body:@{}];
  }
}

- (void)appLoader:(EXUpdatesAppLoader *)appLoader didFailWithError:(NSError *)error
{
  NSLog(@"update failed to load: %@", error.localizedDescription);
  [self _sendEventToBridgeWithType:kEXUpdatesErrorEventName body:@{@"message": error.localizedDescription}];
}

# pragma mark - EXUpdatesAppLauncherDelegate

- (void)appLauncher:(EXUpdatesAppLauncher *)appLauncher didFinishWithSuccess:(BOOL)success
{
  _isReadyToLaunch = YES;
  _launcher = appLauncher;
  [_launchCondition signal];

  if ([self _shouldCheckForUpdate]) {
    _remoteAppLoader.delegate = self;
    [_remoteAppLoader loadUpdateFromUrl:[EXUpdatesConfig sharedInstance].remoteUrl];
  }
}

@end

NS_ASSUME_NONNULL_END
