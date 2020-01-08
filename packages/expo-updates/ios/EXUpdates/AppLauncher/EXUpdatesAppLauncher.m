//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAppController.h>
#import <EXUpdates/EXUpdatesAppLauncher.h>
#import <EXUpdates/EXUpdatesAppLoaderEmbedded.h>
#import <EXUpdates/EXUpdatesDatabase.h>
#import <EXUpdates/EXUpdatesFileDownloader.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesAppLauncher ()

@property (nonatomic, strong, readwrite) EXUpdatesUpdate * _Nullable launchedUpdate;
@property (nonatomic, strong, readwrite) EXUpdatesUpdate * _Nullable launchableUpdate;
@property (nonatomic, strong, readwrite) NSURL * _Nullable launchAssetUrl;
@property (nonatomic, strong, readwrite) NSMutableDictionary * _Nullable assetFilesMap;

@property (nonatomic, strong) EXUpdatesFileDownloader *downloader;

@property (nonatomic, strong) NSLock *lock;
@property (nonatomic, assign) int assetsToDownload;
@property (nonatomic, assign) int assetsToDownloadFinished;

@end

static NSString * const kEXUpdatesAppLauncherErrorDomain = @"AppLauncher";

@implementation EXUpdatesAppLauncher

- (instancetype)init
{
  if (self = [super init]) {
    _lock = [NSLock new];
    _assetsToDownload = 0;
    _assetsToDownloadFinished = 0;
  }
  return self;
}

- (EXUpdatesUpdate * _Nullable)launchableUpdateWithSelectionPolicy:(EXUpdatesSelectionPolicy *)selectionPolicy
{
  if (!_launchableUpdate) {
    EXUpdatesDatabase *database = [EXUpdatesAppController sharedInstance].database;
    NSArray<EXUpdatesUpdate *>* launchableUpdates = [database launchableUpdates];
    _launchableUpdate = [selectionPolicy launchableUpdateFromUpdates:launchableUpdates];
  }
  return _launchableUpdate;
}

- (void)launchUpdateWithSelectionPolicy:(EXUpdatesSelectionPolicy *)selectionPolicy
{
  if (!_launchedUpdate) {
    _launchedUpdate = [self launchableUpdateWithSelectionPolicy:selectionPolicy];
  }
  
  _assetFilesMap = [NSMutableDictionary new];
  NSURL *updatesDirectory = [EXUpdatesAppController sharedInstance].updatesDirectory;

  [_lock lock];
  if (_launchedUpdate) {
    for (EXUpdatesAsset *asset in _launchedUpdate.assets) {
      if ([self ensureAssetExists:asset]) {
        NSURL *assetLocalUrl = [NSURL URLWithString:asset.filename relativeToURL:updatesDirectory];
        if (asset.isLaunchAsset) {
          _launchAssetUrl = assetLocalUrl;
        } else {
          [_assetFilesMap setObject:[assetLocalUrl absoluteString] forKey:[asset.url absoluteString]];
        }
      }
    }
  }

  if (_assetsToDownload == 0 && _delegate) {
    [_delegate appLauncher:self didFinishWithSuccess:YES];
  }
  [_lock unlock];
}

- (BOOL)ensureAssetExists:(EXUpdatesAsset *)asset
{
  NSURL *assetLocalUrl = [NSURL URLWithString:asset.filename relativeToURL:[EXUpdatesAppController sharedInstance].updatesDirectory];
  BOOL assetFileExists = [[NSFileManager defaultManager] fileExistsAtPath:[assetLocalUrl path]];
  if (!assetFileExists) {
    // something has gone wrong, we're missing the asset
    // first check to see if a copy is embedded in the binary
    EXUpdatesUpdate *embeddedManifest = [EXUpdatesAppController sharedInstance].embeddedAppLoader.embeddedManifest;
    if (embeddedManifest) {
      EXUpdatesAsset *matchingAsset;
      for (EXUpdatesAsset *embeddedAsset in embeddedManifest.assets) {
        if ([[embeddedAsset.url absoluteString] isEqualToString:[asset.url absoluteString]]) {
          matchingAsset = embeddedAsset;
          break;
        }
      }

      if (matchingAsset) {
        NSString *bundlePath = [[NSBundle mainBundle] pathForResource:asset.nsBundleFilename ofType:asset.type];
        if ([[NSFileManager defaultManager] copyItemAtPath:bundlePath toPath:[assetLocalUrl path] error:nil]) {
          assetFileExists = YES;
        }
      }
    }
  }

  if (!assetFileExists) {
    // we couldn't copy the file from the embedded assets
    // so we need to attempt to download it
    _assetsToDownload++;
    [self.downloader downloadFileFromURL:asset.url toPath:[assetLocalUrl path] successBlock:^(NSData * _Nonnull data, NSURLResponse * _Nonnull response) {
      asset.data = data;
      asset.response = response;
      asset.downloadTime = [NSDate date];
      [self _assetDownloadDidFinish:asset withLocalUrl:assetLocalUrl];
    } errorBlock:^(NSError * _Nonnull error, NSURLResponse * _Nonnull response) {
      [self _assetDownloadDidError:error];
    }];
  }

  return assetFileExists;
}

// TODO: get rid of this
- (NSUUID * _Nullable)launchedUpdateId
{
  if (!_launchedUpdate) {
    return nil;
  }
  return _launchedUpdate.updateId;
}

- (EXUpdatesFileDownloader *)downloader
{
  if (!_downloader) {
    _downloader = [[EXUpdatesFileDownloader alloc] init];
  }
  return _downloader;
}

- (void)_assetDownloadDidFinish:(EXUpdatesAsset *)asset withLocalUrl:(NSURL *)localUrl
{
  [_lock lock];
  _assetsToDownloadFinished++;
  [[EXUpdatesAppController sharedInstance].database updateAsset:asset];
  if (asset.isLaunchAsset) {
    _launchAssetUrl = localUrl;
  } else {
    [_assetFilesMap setObject:[localUrl absoluteString] forKey:[asset.url absoluteString]];
  }

  if (_assetsToDownloadFinished == _assetsToDownload && _delegate) {
    [_delegate appLauncher:self didFinishWithSuccess:_launchAssetUrl != nil];
  }
  [_lock unlock];
}

- (void)_assetDownloadDidError:(NSError *)error
{
  [_lock lock];
  _assetsToDownloadFinished++;
  if (_assetsToDownloadFinished == _assetsToDownload && _delegate) {
    [_delegate appLauncher:self didFinishWithSuccess:_launchAssetUrl != nil];
  }
  [_lock unlock];
}

@end

NS_ASSUME_NONNULL_END
