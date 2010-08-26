import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

import org.apache.commons.lang.StringUtils;
import org.eclipse.mylyn.internal.wikitext.core.util.css.AnySelector;
import org.eclipse.mylyn.internal.wikitext.core.util.css.Block;
import org.eclipse.mylyn.internal.wikitext.core.util.css.CompositeSelector;
import org.eclipse.mylyn.internal.wikitext.core.util.css.CssClassSelector;
import org.eclipse.mylyn.internal.wikitext.core.util.css.CssParser;
import org.eclipse.mylyn.internal.wikitext.core.util.css.CssRule;
import org.eclipse.mylyn.internal.wikitext.core.util.css.DescendantSelector;
import org.eclipse.mylyn.internal.wikitext.core.util.css.IdSelector;
import org.eclipse.mylyn.internal.wikitext.core.util.css.NameSelector;
import org.eclipse.mylyn.internal.wikitext.core.util.css.Selector;
import org.eclipse.mylyn.internal.wikitext.core.util.css.Stylesheet;

import play.Logger;
import play.Play;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import play.vfs.VirtualFile;

/**
 * SpriteBootstrap permet de générer un fichier Sprite avec vos images de background et de générer la css associée.
 * 
 * TODO ajouter la possibilité de parametrer quel fichier css est scanné
 * TODO ajouter la possibilité de le faire sur plusieurs css
 * TODO parametrer les noms des fichiers .png et .css générés
 * 
 * @author nicogiard
 */
@OnApplicationStart
public class SpriteBootstrap extends Job {

	public void doJob() {
		Logger.info("Lancement du SpriteBootstrap");

		try {
			File mainCssFile = new File(Play.applicationPath.getAbsolutePath() + "/public/stylesheets/main.css");

			Stylesheet style = new CssParser().parse(new FileReader(mainCssFile));

			Map<String, SpriteImage> spriteImages = new HashMap<String, SpriteImage>();
			Pattern patternUrl = Pattern.compile(".*url\\('?\"?([^\"\']*)\"?'?\\).*");

			// Recherche des backgrounds dans la css
			for (Block block : style.getBlocks()) {
				for (CssRule rule : block.getRules()) {
					if (rule.name.contains("background")) {
						// On cherche l'url
						Matcher matcher = patternUrl.matcher(rule.value);
						String url = null;
						while (matcher.find()) {
							url = matcher.group(1);
						}
						// l'url a été trouvée
						if (StringUtils.isNotBlank(url)) {
							VirtualFile vf = VirtualFile.open(new File(Play.applicationPath.getAbsolutePath() + url));
							try {
								String absolutePath = vf.getRealFile().getAbsolutePath();
								if (absolutePath.endsWith(".png")) {
									spriteImages.put(absolutePath, new SpriteImage(block, vf, convert(ImageIO.read(vf.getRealFile()))));
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				}
			}

			File imageDestination = initImageDestination();

			// Lancement de la création du fichier image sprite
			buildSprite(spriteImages, imageDestination);

			File cssDestination = initCssDestination();

			// Lancement de la création du fichier css sprite
			buildCss(spriteImages, cssDestination);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	protected void buildSprite(Map<String, SpriteImage> images, File imageDestination) {
		try {
			// Tri des keys en fonction de la Width des images
			List<String> keys = new ArrayList<String>(images.keySet());
			Collections.sort(keys, new SpriteImageWidthComparator(images));

			// Image finale sera verticale
			int width = 0;
			int height = 0;
			int x = 0;
			int y = 0;

			// Recherche de la width max
			if (keys != null && keys.size() > 0)
				width = images.get(keys.get(0)).bufferedImage.getWidth();

			// MAJ des coordonnées des sprites + recherche de la Heigth max
			for (String key : keys) {
				images.get(key).spriteX = x;
				images.get(key).spriteY = y;

				BufferedImage bufferedImage = images.get(key).bufferedImage;
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
			BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

			for (String key : keys) {
				BufferedImage bufferedImage = images.get(key).bufferedImage;

				Logger.debug("Ajout de l'image '%s'", images.get(key));

				boolean imageDrawn = dest.createGraphics().drawImage(bufferedImage, images.get(key).spriteX, images.get(key).spriteY, null);
				if (!imageDrawn) {
					Logger.debug("Un problème est survenu lors de l'écriture de l'image '%s'", key);
					break;
				}
			}

			// Ecriture du fichier image de Sprite
			ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/png").next();
			ImageWriteParam params = writer.getDefaultWriteParam();
			FileImageOutputStream toFs = new FileImageOutputStream(imageDestination);
			writer.setOutput(toFs);
			IIOImage image = new IIOImage(dest, null, null);
			writer.write(null, image, params);
			toFs.flush();
			toFs.close();

			Logger.debug("Le fichier image Sprite '%s' à été créé avec succès", imageDestination.getAbsolutePath());
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	protected void buildCss(Map<String, SpriteImage> images, File cssDestination) {
		// Tri des keys en fonction de la Width des images
		List<String> keys = new ArrayList<String>(images.keySet());
		Collections.sort(keys, new SpriteImageWidthComparator(images));

		try {
			FileWriter fstream = new FileWriter(cssDestination);
			PrintWriter out = new PrintWriter(fstream);

			StringBuilder sbBackground = new StringBuilder();
			StringBuilder sbEach = new StringBuilder();

			for (String key : keys) {
				// Pour chaque SpriteImage écrire le code css correspondant
				// en se basant sur le block initial

				SpriteImage img = images.get(key);
				String selector = buildSelectorString(img.block.getSelector());

				sbBackground.append(selector).append(", ");

				StringBuilder sb = new StringBuilder();
				sb.append(selector);
				sb.append("{ background-position: ");

				boolean right = false;
				for (CssRule rule : img.block.getRules()) {
					if (rule.name.contains("background")) {
						// TODO faire mieux qu'un contains de right (plutot une regex)
						right = StringUtils.contains(rule.value, "right");
					}
				}

				if (right && img.spriteX == 0) {
					sb.append("right ");
				} else {
					if (img.spriteX > 0)
						sb.append("-");
					sb.append(img.spriteX).append("px ");
				}
				if (img.spriteY > 0)
					sb.append("-");
				sb.append(img.spriteY).append("px; ");
				sb.append("}");
				
				sbEach.append(sb.toString()).append('\n');
				Logger.debug(sb.toString());
			}

			sbBackground = new StringBuilder(sbBackground.substring(0, sbBackground.length() - 2)).append(" { background: white url(/public/images/play-sprite.png) no-repeat left top;}");
			out.println(sbBackground.toString());
			Logger.debug(sbBackground.toString());
			out.println(sbEach.toString());

			out.close();
			Logger.debug("Le fichier css Sprite '%s' à été créé avec succès", cssDestination.getAbsolutePath());
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
		}
	}

	/**
	 * Permet de conserver la transparence des PNG
	 * 
	 * @param image
	 * @return image
	 */
	public static BufferedImage convert(BufferedImage image) {
		GraphicsConfiguration configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
		BufferedImage img = configuration.createCompatibleImage(image.getWidth(), image.getHeight(), Transparency.TRANSLUCENT);
		Graphics2D g2 = (Graphics2D) img.getGraphics();
		g2.drawImage(image, 0, 0, null);
		g2.dispose();
		return img;
	}

	/**
	 * Initialise les répertoires & fichiers pour les images
	 * 
	 * @return Le fichier image destination pour le Sprite
	 */
	public static File initImageDestination() {
		// Le répertoire source des images de background
		File imageBackgroundPath = new File(Play.applicationPath.getAbsolutePath() + "/public/images");
		Logger.debug("Chemin vers les images de background : '%s'", imageBackgroundPath.getAbsolutePath());

		// Le fichier image de destination pour les sprites
		File imageDestination = new File(imageBackgroundPath.getAbsolutePath() + "/play-sprite.png");

		// Si le fichier image de destination existe on le supprime
		if (imageDestination.exists()) {
			Logger.debug("Le fichier image de destination Sprite existe déjà. Tentative de suppression...");

			if (imageDestination.delete()) {
				Logger.debug("Suppression de '%s' réussie", imageDestination.getAbsolutePath());
			} else {
				Logger.debug("Un problème est survenu lors de la suppression de '%s'", imageDestination.getAbsolutePath());
			}
		}
		return imageDestination;
	}

	/**
	 * Initialise les répertoires & fichiers pour les css
	 * 
	 * @return Le fichier css destination pour le Sprite
	 */
	public static File initCssDestination() {
		// Le répertoire de destination pour le css
		File cssBackgroundPath = new File(Play.applicationPath.getAbsolutePath() + "/public/stylesheets");

		// Le fichier css de destination pour les sprites
		File cssDestination = new File(cssBackgroundPath.getAbsolutePath() + "/play-sprite.css");

		// Si le fichier css de destination existe on le supprime
		if (cssDestination.exists()) {
			Logger.debug("Le fichier css de destination Sprite existe déjà. Tentative de suppression...");

			if (cssDestination.delete()) {
				Logger.debug("Suppression de '%s' réussie", cssDestination.getAbsolutePath());
			} else {
				Logger.debug("Un problème est survenu lors de la suppression de '%s'", cssDestination.getAbsolutePath());
			}
		}
		return cssDestination;
	}

	/**
	 * Permet de reconstruire la chaine des Selector CSS
	 * 
	 * @param selector
	 *            le Selector root
	 * @return la chaine correspondante
	 */
	public static String buildSelectorString(Selector selector) {
		// TODO a voir, j'ai eu un probleme avec un Selector '.pagination .previous'.
		StringBuilder sb = new StringBuilder();
		if (selector instanceof AnySelector) {
			sb.append("*");
		}
		if (selector instanceof CompositeSelector) {
			for (Selector child : ((CompositeSelector) selector).getComponents()) {
				sb.append(buildSelectorString(child));
			}
		}
		if (selector instanceof CssClassSelector) {
			sb.append(".").append(((CssClassSelector) selector).getCssClass()).append(" ");
		}
		if (selector instanceof DescendantSelector) {
			sb.append(buildSelectorString(((DescendantSelector) selector).getAncestorSelector()));
		}
		if (selector instanceof IdSelector) {
			sb.append("#").append(((IdSelector) selector).getId()).append(" ");
		}
		if (selector instanceof NameSelector) {
			sb.append(((NameSelector) selector).getName());
		}
		return sb.toString();
	}

	/**
	 * Classe capsule contenant un block css, un virtualFile play, des
	 * coordonnées css et un BufferedImage
	 * 
	 * @author nicogiard
	 */
	public class SpriteImage {
		public Block block;
		public VirtualFile virtualFile;
		public int spriteX;
		public int spriteY;
		public BufferedImage bufferedImage;

		public SpriteImage(Block block, VirtualFile virtualFile, BufferedImage bufferedImage) {
			this.block = block;
			this.virtualFile = virtualFile;
			this.spriteX = 0;
			this.spriteY = 0;
			this.bufferedImage = bufferedImage;
		}

		public SpriteImage(Block block, VirtualFile virtualFile, int spriteX, int spriteY, BufferedImage bufferedImage) {
			this.block = block;
			this.virtualFile = virtualFile;
			this.spriteX = spriteX;
			this.spriteY = spriteY;
			this.bufferedImage = bufferedImage;
		}

		@Override
		public String toString() {
			return virtualFile.getRealFile().getAbsolutePath() + " : " + spriteX + "px " + spriteY + "px";
		}
	}

	/**
	 * Classe Comparator permettant de comparer des Images en fonction de leur
	 * width
	 * 
	 * @author nicogiard
	 */
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
}
