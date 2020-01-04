// Copyright 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAppController.h>
#import <EXUpdates/EXUpdatesAppLauncher.h>
#import <EXUpdates/EXUpdatesAppLoaderRemote.h>
#import <EXUpdates/EXUpdatesConfig.h>
#import <EXUpdates/EXUpdatesDatabase.h>
#import <EXUpdates/EXUpdatesFileDownloader.h>
#import <EXUpdates/EXUpdatesModule.h>
#import <EXUpdates/EXUpdatesUpdate.h>

@interface EXUpdatesModule ()

@property (nonatomic, weak) UMModuleRegistry *moduleRegistry;

@property (nonatomic, strong) UMPromiseResolveBlock fetchUpdateResolver;
@property (nonatomic, strong) UMPromiseRejectBlock fetchUpdateRejecter;

@end

@implementation EXUpdatesModule

UM_EXPORT_MODULE(ExpoUpdates);

- (void)setModuleRegistry:(UMModuleRegistry *)moduleRegistry
{
  _moduleRegistry = moduleRegistry;
}

- (NSDictionary *)constantsToExport
{
  EXUpdatesAppController *controller = [EXUpdatesAppController sharedInstance];
  EXUpdatesUpdate *launchedUpdate = controller.launcher.launchedUpdate;
  if (!launchedUpdate) {
    return @{};
  } else {
    return @{
      @"manifest": launchedUpdate.rawManifest,
      // TODO: make this the correct format
      @"localAssets": [controller.database assetsForUpdateId:launchedUpdate.updateId]
    };
  }
  
}

UM_EXPORT_METHOD_AS(reload,
                    reloadAsync:(UMPromiseResolveBlock)resolve
                         reject:(UMPromiseRejectBlock)reject)
{
  BOOL success = [[EXUpdatesAppController sharedInstance] reloadBridge];
  if (success) {
    resolve(nil);
  } else {
    reject(@"ERR_UPDATES_RELOAD", @"Could not reload application. Ensure you have set the `bridge` property of EXUpdatesAppController.", nil);
  }
}

UM_EXPORT_METHOD_AS(checkForUpdateAsync,
                    checkForUpdateAsync:(UMPromiseResolveBlock)resolve
                                 reject:(UMPromiseRejectBlock)reject)
{
  if (![EXUpdatesAppController sharedInstance].isEnabled) {
    reject(@"ERR_UPDATES_CHECK", @"The updates module controller has not been properly initialized. If you're in development mode, you cannot check for updates. Otherwise, make sure you have called [[EXUpdatesAppController sharedInstance] start].", nil);
    return;
  }

  NSURLSessionConfiguration *configuration = NSURLSessionConfiguration.defaultSessionConfiguration;
  configuration.requestCachePolicy = NSURLRequestReloadIgnoringCacheData;
  EXUpdatesFileDownloader *fileDownloader = [[EXUpdatesFileDownloader alloc] initWithURLSessionConfiguration:configuration];
  [fileDownloader downloadManifestFromURL:[EXUpdatesConfig sharedInstance].remoteUrl successBlock:^(EXUpdatesUpdate * _Nonnull update) {
    EXUpdatesUpdate *launchedUpdate = [EXUpdatesAppController sharedInstance].launcher.launchedUpdate;
    EXUpdatesSelectionPolicy *selectionPolicy = [EXUpdatesAppController sharedInstance].selectionPolicy;
    if ([selectionPolicy shouldLoadNewUpdate:update withLaunchedUpdate:launchedUpdate]) {
      resolve(update.rawManifest);
    } else {
      resolve(@(NO));
    }
  } errorBlock:^(NSError * _Nonnull error, NSURLResponse * _Nonnull response) {
    reject(@"ERR_UPDATES_CHECK", error.localizedDescription, error);
  }];
}

UM_EXPORT_METHOD_AS(fetchUpdateAsync,
                    fetchUpdateAsync:(UMPromiseResolveBlock)resolve
                              reject:(UMPromiseRejectBlock)reject)
{
  if (![EXUpdatesAppController sharedInstance].isEnabled) {
    reject(@"ERR_UPDATES_FETCH", @"The updates module controller has not been properly initialized. If you're in development mode, you cannot fetch updates. Otherwise, make sure you have called [[EXUpdatesAppController sharedInstance] start].", nil);
    return;
  }
  
  if (_fetchUpdateResolver || _fetchUpdateRejecter) {
    reject(@"ERR_UPDATES_FETCH", @"An update is already being fetched. Wait for the first call to `fetchUpdateAsync()` to resolve before calling it again.", nil);
    return;
  }
  
  _fetchUpdateResolver = resolve;
  _fetchUpdateRejecter = reject;

  EXUpdatesAppLoaderRemote *remoteAppLoader = [[EXUpdatesAppLoaderRemote alloc] init];
  remoteAppLoader.delegate = self;
  [remoteAppLoader loadUpdateFromUrl:[EXUpdatesConfig sharedInstance].remoteUrl];
}

# pragma mark - EXUpdatesAppLoaderDelegate

- (BOOL)appLoader:(EXUpdatesAppLoader *)appLoader shouldStartLoadingUpdate:(EXUpdatesUpdate *)update
{
  EXUpdatesUpdate *launchedUpdate = [EXUpdatesAppController sharedInstance].launcher.launchedUpdate;
  EXUpdatesSelectionPolicy *selectionPolicy = [EXUpdatesAppController sharedInstance].selectionPolicy;
  return [selectionPolicy shouldLoadNewUpdate:update withLaunchedUpdate:launchedUpdate];
}

- (void)appLoader:(EXUpdatesAppLoader *)appLoader didFinishLoadingUpdate:(EXUpdatesUpdate * _Nullable)update
{
  if (_fetchUpdateResolver) {
    if (update) {
      _fetchUpdateResolver(update.rawManifest);
    } else {
      _fetchUpdateResolver(@(NO));
    }
  }
  _fetchUpdateResolver = nil;
  _fetchUpdateRejecter = nil;
}

- (void)appLoader:(EXUpdatesAppLoader *)appLoader didFailWithError:(NSError *)error
{
  if (_fetchUpdateRejecter) {
    _fetchUpdateRejecter(@"ERR_UPDATES_FETCH", @"Failed to download new update", error);
  }
  _fetchUpdateResolver = nil;
  _fetchUpdateRejecter = nil;
}

@end
