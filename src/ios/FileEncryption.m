#import "FileEncryption.h"

NSString* const ENCRYPT_FILE_MESSAGE_ID = @"ENCRYPT_FILE";

@implementation FileEncryption

- (void) pluginInitialize
{
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(encryptFileNotification:) name:ENCRYPT_FILE_MESSAGE_ID object:nil];
}

- (void)encryptFileNotification:(NSNotification *)notification
{
    [self encrypt:notification.object];
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
    
    NSString *path = [command.arguments objectAtIndex:0];
    path = [path stringByReplacingOccurrencesOfString:@"file://" withString:@""];
    NSFileManager *fileManager = [NSFileManager defaultManager];
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