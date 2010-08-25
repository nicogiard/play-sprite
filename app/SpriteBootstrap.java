import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

import play.Logger;
import play.Play;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import play.vfs.VirtualFile;

@OnApplicationStart
public class SpriteBootstrap extends Job {
	public void doJob() {
		Logger.info("Lancement du SpriteBootstrap");

		File backgroundPath = new File(Play.applicationPath.getAbsolutePath() + "/public/images/background");
		Logger.debug("Chemin vers les images de background : '%s'", backgroundPath.getAbsolutePath());

		File final_image = new File(backgroundPath.getAbsolutePath() + "/background.png");
		if (final_image.exists()) {
			Logger.debug("Le fichier image de destination Sprite existe déjà. Tentative de suppression...");
			if (final_image.delete()) {
				Logger.debug("Suppression de '%s' réussie", final_image.getAbsolutePath());
			} else {
				Logger.debug("Un problème est survenu lors de la suppression de '%s'", final_image.getAbsolutePath());
			}
		}

		VirtualFile vf = VirtualFile.open(backgroundPath);
		Map<VirtualFile, SpriteImage> listBImage = new HashMap<VirtualFile, SpriteImage>();

		try {
			for (VirtualFile child : vf.list()) {
				listBImage.put(child, new SpriteImage(child.getRealFile().getAbsolutePath(), ImageIO.read(child.getRealFile())));
			}

			// Image finale sera verticale
			int width = 0;
			int height = 0;
			for (VirtualFile key : listBImage.keySet()) {
				BufferedImage bufferedImage = listBImage.get(key).bufferedImage;
				if (width < bufferedImage.getWidth())
					width = bufferedImage.getWidth();

				height += bufferedImage.getHeight();
			}
			Logger.debug("Taille de l'image Sprite : '%sx%s'", width, height);

			// Concatenation des images
			BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			int x = 0;
			int y = 0;
			for (VirtualFile key : listBImage.keySet()) {
				listBImage.get(key).spriteX = x + "px";
				listBImage.get(key).spriteY = y + "px";
				BufferedImage bufferedImage = listBImage.get(key).bufferedImage;
				Logger.debug("Ajout de l'image '%s'", key.getName());

				boolean imageDrawn = dest.createGraphics().drawImage(bufferedImage, x, y, null);

				if (!imageDrawn) {
					Logger.debug("Un problème est survenu lors de l'écriture de l'image '%s'", key.getName());
					break;
				} else {
					y += bufferedImage.getHeight();
				}
			}

			ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/png").next();
			ImageWriteParam params = writer.getDefaultWriteParam();
			FileImageOutputStream toFs = new FileImageOutputStream(final_image);
			writer.setOutput(toFs);
			IIOImage image = new IIOImage(dest, null, null);
			writer.write(null, image, params);
			toFs.flush();
			toFs.close();

			Logger.debug("Le fichier image Sprite '%s' à été créé avec succès", final_image.getAbsolutePath());
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		for (VirtualFile image : listBImage.keySet()) {
			Logger.debug("%s", listBImage.get(image));
		}
	}

	public class SpriteImage {
		public String absolutePath;
		public String spriteX;
		public String spriteY;
		public BufferedImage bufferedImage;

		public SpriteImage(String absolutePath, BufferedImage bufferedImage) {
			this.absolutePath = absolutePath;
			this.spriteX = "0";
			this.spriteY = "0";
			this.bufferedImage = bufferedImage;
		}

		public SpriteImage(String absolutePath, String spriteX, String spriteY, BufferedImage bufferedImage) {
			this.absolutePath = absolutePath;
			this.spriteX = spriteX;
			this.spriteY = spriteY;
			this.bufferedImage = bufferedImage;
		}

		@Override
		public String toString() {
			return absolutePath + " : " + spriteX + " " + spriteY;
		}
	}
}
