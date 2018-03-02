#import "FileEncryption.h"

@implementation FileEncryption

- (void) pluginInitialize
{
}

- (void)encrypt:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult *pluginResult = nil;
    
    NSString *path = [self crypto:@"encrypt" command:command];
        
    if (path != nil) {
        pluginResult =
        [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                          messageAsString:path];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult
                                callbackId:command.callbackId];
}

- (void)decrypt:(CDVInvokedUrlCommand *)command {
    
    CDVPluginResult *pluginResult = nil;
    
    NSString *path = [self crypto:@"decrypt" command:command];
        
    if (path != nil) {
        pluginResult =
        [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                          messageAsString:path];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult
                                callbackId:command.callbackId];
}

- (NSString*)crypto:(NSString *)action command:(CDVInvokedUrlCommand *)command {
    
    NSString *filePath = [command.arguments objectAtIndex:0];
    NSString *fileName = [filePath lastPathComponent];
    NSFileManager *fileManager = [NSFileManager defaultManager];
    NSString *documentsPath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) firstObject];
    NSString *path = [documentsPath stringByAppendingPathComponent:fileName];
    BOOL fileExists = [fileManager fileExistsAtPath:path];
    
    if (path != nil && [path length] > 0) {

        if (fileExists) {
            
            if ([action isEqualToString:@"encrypt"]) {
                
                NSData *fileData = [NSData dataWithContentsOfFile:path];
                [fileManager createFileAtPath:path contents:fileData attributes:[NSDictionary dictionaryWithObject:NSFileProtectionComplete forKey:NSFileProtectionKey]];
                return path;
                
            } else if ([action isEqualToString:@"decrypt"]) {
                
                return path;
            }
            
        } else {
            path = nil;
        }
    }
    
    return nil;
}

@end