//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAppLoader.h>
#import <UMCore/UMExportedModule.h>
#import <UMCore/UMModuleRegistryConsumer.h>

@interface EXUpdatesModule : UMExportedModule <UMModuleRegistryConsumer, EXUpdatesAppLoaderDelegate>
@end
