import Foundation
import UIKit
import MobileCoreServices
import ImageIO

@objc(ImageResizerSwift)
public class ImageResizerSwift: NSObject {

    @objc
    public static func generatePath(ext: String, outputPath: String) throws -> String {
        var directory: String
        let fileManager = FileManager.default
        
        if outputPath.isEmpty {
            let paths = NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true)
            directory = paths[0]
        } else {
            let paths = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)
            let documentsDirectory = paths[0]
            if outputPath.hasPrefix(documentsDirectory) {
                directory = outputPath
            } else {
                directory = (documentsDirectory as NSString).appendingPathComponent(outputPath)
            }
            
            do {
                try fileManager.createDirectory(atPath: directory, withIntermediateDirectories: true, attributes: nil)
            } catch {
                throw NSError(domain: "ImageResizer", code: 0, userInfo: [NSLocalizedDescriptionKey: "Error creating documents subdirectory: \(error.localizedDescription)"])
            }
        }
        
        let name = UUID().uuidString
        let fullName = "\(name).\(ext)"
        let fullPath = (directory as NSString).appendingPathComponent(fullName)
        
        return fullPath
    }
    
    @objc
    public static func rotateImage(_ inputImage: UIImage, rotationDegrees: Float) -> UIImage? {
        let rotDiv90 = Int(round(rotationDegrees / 90.0))
        let rotQuadrant = rotDiv90 % 4
        let rotQuadrantAbs = (rotQuadrant < 0) ? rotQuadrant + 4 : rotQuadrant
        
        if rotQuadrantAbs == 0 {
            return inputImage
        }
        
        var orientation: UIImage.Orientation = .up
        switch rotQuadrantAbs {
        case 1:
            orientation = .right
        case 2:
            orientation = .down
        default:
            orientation = .left
        }
        
        guard let cgImage = inputImage.cgImage else { return inputImage }
        return UIImage(cgImage: cgImage, scale: 1.0, orientation: orientation)
    }
    
    @objc
    public static func getScaleForProportionalResizing(theSize: CGSize, intoSize: CGSize, onlyScaleDown: Bool, maximize: Bool) -> Float {
        let sx = Float(theSize.width)
        let sy = Float(theSize.height)
        var dx = Float(intoSize.width)
        var dy = Float(intoSize.height)
        var scale: Float = 1.0
        
        if sx != 0 && sy != 0 {
            dx = dx / sx
            dy = dy / sy
            
            if maximize {
                scale = max(dx, dy)
            } else {
                scale = min(dx, dy)
            }
            
            if onlyScaleDown {
                scale = min(scale, 1.0)
            }
        } else {
            scale = 0.0
        }
        return scale
    }
    
    @objc
    public static func scaleImage(_ image: UIImage, toSize: CGSize, mode: String, onlyScaleDown: Bool) -> UIImage? {
        let imageSize = CGSize(width: image.size.width * image.scale, height: image.size.height * image.scale)
        var newSize: CGSize
        
        if mode == "stretch" {
            var width = toSize.width
            var height = toSize.height
            if onlyScaleDown {
                width = min(width, imageSize.width)
                height = min(height, imageSize.height)
            }
            newSize = CGSize(width: width, height: height)
        } else {
            let maximize = (mode == "cover")
            let scale = getScaleForProportionalResizing(theSize: imageSize, intoSize: toSize, onlyScaleDown: onlyScaleDown, maximize: maximize)
            newSize = CGSize(width: round(imageSize.width * CGFloat(scale)), height: round(imageSize.height * CGFloat(scale)))
        }
        
        UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
        image.draw(in: CGRect(x: 0, y: 0, width: newSize.width, height: newSize.height))
        let newImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return newImage
    }
    
    @objc
    public static func getImageMetaData(path: String) -> NSMutableDictionary? {
        if path.hasPrefix("assets-library") {
            return nil
        } else {
            var imageData: Data?
            if path.hasPrefix("data:") || path.hasPrefix("file:") {
                if let url = URL(string: path) {
                    imageData = try? Data(contentsOf: url)
                }
            } else {
                imageData = try? Data(contentsOf: URL(fileURLWithPath: path))
            }
            
            guard let data = imageData as CFData?, let source = CGImageSourceCreateWithData(data, nil) else {
                return nil
            }
            
            if let metaRef = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any] {
                return NSMutableDictionary(dictionary: metaRef)
            }
            return nil
        }
    }
    
    @objc
    public static func saveImage(_ fullPath: String, image: UIImage, format: String, quality: Float, metadata: NSMutableDictionary?) -> Bool {
        if let meta = metadata {
            var imgType: CFString = kUTTypeJPEG
            if format == "JPEG" {
                meta[kCGImageDestinationLossyCompressionQuality as String] = NSNumber(value: quality / 100.0)
            } else if format == "PNG" {
                imgType = kUTTypePNG
            } else {
                return false
            }
            
            let destData = NSMutableData()
            guard let destination = CGImageDestinationCreateWithData(destData as CFMutableData, imgType, 1, nil) else {
                return false
            }
            
            guard let cgImage = image.cgImage else { return false }
            CGImageDestinationAddImage(destination, cgImage, meta as CFDictionary)
            
            if CGImageDestinationFinalize(destination) {
                let fileManager = FileManager.default
                return fileManager.createFile(atPath: fullPath, contents: destData as Data, attributes: nil)
            } else {
                return false
            }
        } else {
            var data: Data?
            if format == "JPEG" {
                data = image.jpegData(compressionQuality: CGFloat(quality / 100.0))
            } else if format == "PNG" {
                data = image.pngData()
            }
            
            guard let safeData = data else { return false }
            let fileManager = FileManager.default
            return fileManager.createFile(atPath: fullPath, contents: safeData, attributes: nil)
        }
    }
    
    @objc
    public static func transformImage(
        image originalImage: UIImage,
        originalPath: String,
        rotation: Int,
        newSize: CGSize,
        fullPath: String,
        format: String,
        quality: Int,
        keepMeta: Bool,
        options: [String: Any]
    ) throws -> [String: Any] {
        
        var image: UIImage? = originalImage
        if rotation != 0 {
            image = rotateImage(originalImage, rotationDegrees: Float(rotation))
            if image == nil {
                throw NSError(domain: "ImageResizer", code: 0, userInfo: [NSLocalizedDescriptionKey: "Can't rotate the image."])
            }
        }
        
        let mode = options["mode"] as? String ?? "contain"
        let onlyScaleDown = options["onlyScaleDown"] as? Bool ?? false
        
        guard let safeImage = image, let scaledImage = scaleImage(safeImage, toSize: newSize, mode: mode, onlyScaleDown: onlyScaleDown) else {
            throw NSError(domain: "ImageResizer", code: 0, userInfo: [NSLocalizedDescriptionKey: "Can't resize the image."])
        }
        
        var metadata: NSMutableDictionary? = nil
        if keepMeta && format == "JPEG" {
            metadata = getImageMetaData(path: originalPath)
            if let meta = metadata {
                meta[kCGImagePropertyOrientation as String] = NSNumber(value: 1)
            }
        }
        
        if !saveImage(fullPath, image: scaledImage, format: format, quality: Float(quality), metadata: metadata) {
            throw NSError(domain: "ImageResizer", code: 0, userInfo: [NSLocalizedDescriptionKey: "Can't save the image. Check your compression format and your output path"])
        }
        
        let fileUrl = URL(fileURLWithPath: fullPath)
        let fileName = fileUrl.lastPathComponent
        let attributes = try? FileManager.default.attributesOfItem(atPath: fullPath)
        let fileSize = attributes?[.size] as? NSNumber ?? NSNumber(value: 0)
        
        let response: [String: Any] = [
            "path": fullPath,
            "uri": fileUrl.absoluteString,
            "name": fileName,
            "size": fileSize,
            "width": NSNumber(value: Float(scaledImage.size.width)),
            "height": NSNumber(value: Float(scaledImage.size.height))
        ]
        
        return response
    }
}
