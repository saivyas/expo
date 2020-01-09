//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAppController.h>
#import <EXUpdates/EXUpdatesAppLoaderRemote.h>
#import <EXUpdates/EXUpdatesCrypto.h>
#import <EXUpdates/EXUpdatesFileDownloader.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesAppLoaderRemote ()

@property (nonatomic, strong) EXUpdatesFileDownloader *downloader;

@end

@implementation EXUpdatesAppLoaderRemote

- (instancetype)init
{
  if (self = [super init]) {
    _downloader = [[EXUpdatesFileDownloader alloc] init];
  }
  return self;
}

/**
 * we expect the server to respond with a JSON object with the following fields:
 * id (UUID string)
 * commitTime (timestamp number)
 * binaryVersions (comma separated list - string)
 * bundleUrl (string)
 * metadata (arbitrary object)
 * assets (array of asset objects with `url` and `type` keys)
 */

- (void)loadUpdateFromUrl:(NSURL *)url
{
  [_downloader downloadManifestFromURL:url successBlock:^(EXUpdatesUpdate * _Nonnull update) {
    [self startLoadingFromManifest:update];
  } errorBlock:^(NSError * _Nonnull error, NSURLResponse * _Nonnull response) {
    if (self.delegate) {
      [self.delegate appLoader:self didFailWithError:error];
    }
  }];
}

- (void)downloadAsset:(EXUpdatesAsset *)asset
{
  NSURL *updatesDirectory = [EXUpdatesAppController sharedInstance].updatesDirectory;
  NSURL *urlOnDisk = [updatesDirectory URLByAppendingPathComponent:asset.filename];
  if ([[NSFileManager defaultManager] fileExistsAtPath:[urlOnDisk path]]) {
    // file already exists, we don't need to download it again
    [self handleAssetDownloadAlreadyExists:asset];
  } else {
    [_downloader downloadFileFromURL:asset.url toPath:[urlOnDisk path] successBlock:^(NSData * data, NSURLResponse * response) {
      [self handleAssetDownloadWithData:data response:response asset:asset];
    } errorBlock:^(NSError * error, NSURLResponse * response) {
      [self handleAssetDownloadWithError:error asset:asset];
    }];
  }
}

@end

NS_ASSUME_NONNULL_END
