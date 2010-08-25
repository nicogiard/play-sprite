import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
		Map<String, SpriteImage> listBImage = new HashMap<String, SpriteImage>();

		try {
			for (VirtualFile child : vf.list()) {
				String absolutePath = child.getRealFile().getAbsolutePath();
				listBImage.put(absolutePath, new SpriteImage(absolutePath, ImageIO.read(child.getRealFile())));
			}

			// Tri des keys en fonction de la Width des images
			List<String> keys = new ArrayList<String>(listBImage.keySet());
			Collections.sort(keys, new SpriteImageWidthComparator(listBImage));

			// Image finale sera verticale
			int width = 0;
			int height = 0;
			int x = 0;
			int y = 0;

			if (keys != null && keys.size() > 0)
				width = listBImage.get(keys.get(0)).bufferedImage.getWidth();

			for (String key : keys) {
				listBImage.get(key).spriteX = x;
				listBImage.get(key).spriteY = y;

				BufferedImage bufferedImage = listBImage.get(key).bufferedImage;
				if (x + bufferedImage.getWidth() < width) {
					x += bufferedImage.getWidth();
					height = y + bufferedImage.getHeight();
				} else {
					x = 0;
					y += bufferedImage.getHeight();
				}
			}
			Logger.debug("Taille de l'image Sprite : '%sx%s'", width, height);

			// Concatenation des images
			BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

			for (String key : keys) {
				BufferedImage bufferedImage = listBImage.get(key).bufferedImage;

				Logger.debug("Ajout de l'image '%s'", listBImage.get(key));

				boolean imageDrawn = dest.createGraphics().drawImage(bufferedImage, listBImage.get(key).spriteX, listBImage.get(key).spriteY, null);

				if (!imageDrawn) {
					Logger.debug("Un problème est survenu lors de l'écriture de l'image '%s'", key);
					break;
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
	}

	public class SpriteImage {
		public String absolutePath;
		public int spriteX;
		public int spriteY;
		public BufferedImage bufferedImage;

		public SpriteImage(String absolutePath, BufferedImage bufferedImage) {
			this.absolutePath = absolutePath;
			this.spriteX = 0;
			this.spriteY = 0;
			this.bufferedImage = bufferedImage;
		}

		public SpriteImage(String absolutePath, int spriteX, int spriteY, BufferedImage bufferedImage) {
			this.absolutePath = absolutePath;
			this.spriteX = spriteX;
			this.spriteY = spriteY;
			this.bufferedImage = bufferedImage;
		}

		@Override
		public String toString() {
			return absolutePath + " : " + spriteX + "px " + spriteY + "px";
		}
	}

	public class SpriteImageWidthComparator implements Comparator<String> {
		private Map<String, SpriteImage> images;

		public SpriteImageWidthComparator(Map<String, SpriteImage> images) {
			this.images = images;
		}

		public int compare(String id1, String id2) {
			SpriteImage i1 = images.get(id1);
			SpriteImage i2 = images.get(id2);
			return i2.bufferedImage.getWidth() - i1.bufferedImage.getWidth();
		}
	}

	public class SpriteImageHeightComparator implements Comparator<String> {
		private Map<String, SpriteImage> images;

		public SpriteImageHeightComparator(Map<String, SpriteImage> images) {
			this.images = images;
		}

		public int compare(String id1, String id2) {
			SpriteImage i1 = images.get(id1);
			SpriteImage i2 = images.get(id2);
			return i2.bufferedImage.getHeight() - i1.bufferedImage.getHeight();
		}
	}
}
