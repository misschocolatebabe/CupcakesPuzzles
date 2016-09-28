package com.bigkidsapps.cupcakespuzzles;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.method.LinkMovementMethod;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.bigkidsapps.cupcakespuzzles.PuzzleView.ShowNumbers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class PuzzleActivity extends Activity
{
	protected static final int MENU_SCRAMBLE = 0;
	protected static final int MENU_NEW_IMAGE = 1;

	protected static final int MENU_SHOW_NUMBERS = 4;
	protected static final int MENU_CHANGE_TILING = 5;
	protected static final int MENU_STATS = 6;
	protected static final int MENU_ABOUT = 7;

	protected static final int MENU_MAIN = 8;


	protected static final int MIN_WIDTH = 2;
	protected static final int MAX_WIDTH = 6;

	protected static final String KEY_SHOW_NUMBERS = "showNumbers";
	protected static final String KEY_IMAGE_URI = "imageUri";
	protected static final String KEY_PUZZLE = "puzzle";
	protected static final String KEY_PUZZLE_SIZE = "puzzleSize";
	protected static final String KEY_MOVE_COUNT = "moveCount";

	protected static final String KEY_MOVE_BEST_PREFIX = "moveBest_";
	protected static final String KEY_MOVE_AVERAGE_PREFIX = "moveAvg_";
	protected static final String KEY_PLAYS_PREFIX = "plays_";
	
	protected static final String FILENAME_DIR = "com.bigkidsapps.cupcakespuzzles";
	protected static final String FILENAME_PHOTO_DIR = FILENAME_DIR + "/photo";
	protected static final String FILENAME_PHOTO = "photo.jpg";
	protected static final String FILENAME_IMAGE_PREFIX = "image";
	protected static final String FILENAME_DISABLE_INTERNAL_IMAGES = ".disable_internal_images";
	protected static final String SUFFIX_JPG = ".jpg";
	protected static final String SUFFIX_JPEG = ".jpeg";
	protected static final String SUFFIX_PNG = ".png";

	protected static final int DEFAULT_SIZE = 3;

	protected String[] LABELS_SHOW_NUMBERS_ITEMS;
	
	private PuzzleView view;
	private Puzzle puzzle;
	private Options bitmapOptions;
	private MenuItem menuShowNumbers;
	private int puzzleWidth = 1;
	private int puzzleHeight = 1;
	private Uri imageUri;
	private boolean portrait;
	private boolean expert;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		LABELS_SHOW_NUMBERS_ITEMS = new String[]
		{
			getString(R.string.label_show_numbers_none),
			getString(R.string.label_show_numbers_some),
			getString(R.string.label_show_numbers_all)
		};
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		bitmapOptions = new Options();
		bitmapOptions.inScaled = false;

		puzzle = new Puzzle();

		view = new PuzzleView(this, puzzle);
		ButtonView buttonView = new ButtonView(this, view);
		setContentView(buttonView);

		scramble();
		
		if(!loadPreferences())
		{
			setPuzzleSize(DEFAULT_SIZE, true);
		}

		if(imageUri == null)
		{
			newImage();
		}
	}

	private void scramble()
	{
		puzzle.init(puzzleWidth, puzzleHeight);
		puzzle.scramble();
		view.invalidate();
		expert = view.getShowNumbers() == ShowNumbers.NONE;
	}

	protected void newImage()
	{
		LinkedList<Uri> availableImages = new LinkedList<Uri>();
		findExternalImages(availableImages);
		
		if(availableImages.isEmpty() || isUseInternalImages())
		{
			findInternalImages(availableImages);
		}
		
		if(availableImages.isEmpty())
		{
			Toast.makeText(this, getString(R.string.error_could_not_load_image), Toast.LENGTH_LONG).show();
			return;
		}
		
		int index = new Random().nextInt(availableImages.size());
		loadBitmap(availableImages.get(index));
	}

	protected void findInternalImages(List<Uri> list)
	{
		String baseUri = getResourceBaseUri();
		Field[] fields = R.drawable.class.getFields();

		for(Field field: fields)
		{
			String name = field.getName();
			
			if(name.startsWith(FILENAME_IMAGE_PREFIX))
			{
				int id = getResources().getIdentifier(name, "drawable", getPackageName());
				list.add(Uri.parse(baseUri + id));
			}
		}
	}

	protected boolean isUseInternalImages()
	{
		File root = new File(Environment.getExternalStorageDirectory().getPath());
		File dir = new File(root, FILENAME_DIR);
		
		if(!dir.exists())
		{
			return false;
		}
		
		File file = new File(dir, FILENAME_DISABLE_INTERNAL_IMAGES);
		
		return !file.exists();
	}
	
	protected void findExternalImages(List<Uri> list)
	{
		File root = new File(Environment.getExternalStorageDirectory().getPath());
		File dir = new File(root, FILENAME_DIR);
		
		if(!dir.exists())
		{
			return;
		}

		File[] files = dir.listFiles(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String filename)
			{
				filename = filename.toLowerCase(Locale.getDefault());
				
				return filename.endsWith(SUFFIX_JPG) || 
						filename.endsWith(SUFFIX_JPEG) || 
						filename.endsWith(SUFFIX_PNG);
			}
		});
		
		for(File file: files)
		{
			list.add(Uri.fromFile(file));
		}
	}
	
	protected void loadBitmap(Uri uri)
	{
		try
		{
			Options o = new Options();
			o.inJustDecodeBounds = true;

			InputStream imageStream = getContentResolver().openInputStream(uri);
			BitmapFactory.decodeStream(imageStream, null, o);

			int targetWidth = view.getTargetWidth();
			int targetHeight = view.getTargetHeight();

			if(o.outWidth > o.outHeight && targetWidth < targetHeight)
			{
				int i = targetWidth;
				targetWidth = targetHeight;
				targetHeight = i;
			}

			if(targetWidth < o.outWidth || targetHeight < o.outHeight)
			{
				double widthRatio = (double) targetWidth / (double) o.outWidth;
				double heightRatio = (double) targetHeight / (double) o.outHeight;
				double ratio = Math.max(widthRatio, heightRatio);

				o.inSampleSize = (int) Math.pow(2, (int) Math.round(Math.log(ratio) / Math.log(0.5)));
			}
			else
			{
				o.inSampleSize = 1;
			}

			o.inScaled = false;
			o.inJustDecodeBounds = false;

			imageStream = getContentResolver().openInputStream(uri);
			Bitmap bitmap = BitmapFactory.decodeStream(imageStream, null, o);

			if(bitmap == null)
			{
				Toast.makeText(this, getString(R.string.error_could_not_load_image), Toast.LENGTH_LONG).show();
				return;
			}
			
			int rotate = 0;

			Cursor cursor = getContentResolver().query(uri, new String[] {MediaStore.Images.ImageColumns.ORIENTATION}, null, null, null);
			
			if(cursor != null)
			{
				try
				{
					if(cursor.moveToFirst())
					{
						rotate = cursor.getInt(0);
						
						if(rotate == -1)
						{
							rotate = 0;
						}
					}
				}
				finally
				{
					cursor.close();
				}
			}
			
			if(rotate != 0)
			{
				Matrix matrix = new Matrix();
				matrix.postRotate(rotate);
				
				bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
			}
			
			setBitmap(bitmap);
			imageUri = uri;
		}
		catch(FileNotFoundException ex)
		{
			Toast.makeText(this, MessageFormat.format(getString(R.string.error_could_not_load_image_error), ex.getMessage()), Toast.LENGTH_LONG).show();
			return;
		}
	}

	private void setBitmap(Bitmap bitmap)
	{
		portrait = bitmap.getWidth() < bitmap.getHeight();

		view.setBitmap(bitmap);
		setPuzzleSize(Math.min(puzzleWidth, puzzleHeight), true);

		setRequestedOrientation(portrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	}

	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent)
	{
		super.onActivityResult(requestCode, resultCode, imageReturnedIntent);

	}

	private File getSaveDirectory()
	{
		File root = new File(Environment.getExternalStorageDirectory().getPath());
		File dir = new File(root, FILENAME_PHOTO_DIR);
		
		if(!dir.exists())
		{
			if(!root.exists() || !dir.mkdirs())
			{
				return null;
			}
		}

		return dir;
	}
	
	private void changeTiling()
	{
		float ratio = getImageAspectRatio();
		
		if(ratio < 1)
		{
			ratio = 1f / ratio;
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.title_select_tiling);

		String[] items = new String[MAX_WIDTH - MIN_WIDTH + 1];
		int selected = 0;
		
		for(int i = 0; i < items.length; i++)
		{
			int width;
			int height;
			
			if(portrait)
			{
				width = i + MIN_WIDTH;
				height = (int) (width * ratio);
			}
			else
			{
				height = i + MIN_WIDTH;
				width = (int) (height * ratio);
			}
			
			items[i] = sizeToString(width, height);
			
			if(i + MIN_WIDTH == Math.min(puzzleWidth, puzzleHeight))
			{
				selected = i;
			}
		}
		
		builder.setSingleChoiceItems(items, selected, new OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				setPuzzleSize(which + MIN_WIDTH, false);
				dialog.dismiss();
			}
		});

		builder.create().show();
	}

	private float getImageAspectRatio()
	{
		Bitmap bitmap = view.getBitmap();
		
		if(bitmap == null)
		{
			return 1;
		}
		
		float width = bitmap.getWidth();
		float height = bitmap.getHeight();
		
		return width / height;
	}

	protected void setPuzzleSize(int size, boolean scramble)
	{
		float ratio = getImageAspectRatio();
		
		if(ratio < 1)
		{
			ratio = 1f /ratio;
		}
		
		int newWidth;
		int newHeight;
		
		if(portrait)
		{
			newWidth = size;
			newHeight = (int) (size * ratio); 
		}
		else
		{
			newWidth = (int) (size * ratio); 
			newHeight = size;
		}
		
		if(scramble || newWidth != puzzleWidth || newHeight != puzzleHeight)
		{
			puzzleWidth = newWidth;
			puzzleHeight = newHeight;
			scramble();
		}
	}

	protected String sizeToString(int width, int height)
	{
		return MessageFormat.format(getString(R.string.puzzle_size_x_y), width, height);
	}

	private void toggleShowNumbers()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.title_show_numbers);

		int selected = showNumbersToInt(view.getShowNumbers());
		
		builder.setSingleChoiceItems(LABELS_SHOW_NUMBERS_ITEMS, selected, new OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				ShowNumbers oldShowNumbers = view.getShowNumbers();
				ShowNumbers newShowNumbers = intToShowNumbers(which);
				
				if(newShowNumbers != oldShowNumbers)
				{
					view.setShowNumbers(newShowNumbers);
					updateMenuShowNumbers();
					view.invalidate();
					
					if(oldShowNumbers == ShowNumbers.NONE && expert)
					{
						Toast.makeText(PuzzleActivity.this, R.string.toast_disabled_expert_mode, Toast.LENGTH_LONG).show();
						expert = false;
					}
					else if(newShowNumbers == ShowNumbers.NONE)
					{
						Toast.makeText(PuzzleActivity.this, R.string.toast_not_enabled_expert_mode, Toast.LENGTH_LONG).show();
					}
				}
				
				dialog.dismiss();
			}
		});

		builder.create().show();
	}

	protected void updateMenuShowNumbers()
	{
		menuShowNumbers.setTitle(MessageFormat.format(getString(R.string.menu_show_numbers), LABELS_SHOW_NUMBERS_ITEMS[showNumbersToInt(view.getShowNumbers())]));
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);
		
		onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		boolean hasCamera = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA); 

		menu.add(0, MENU_NEW_IMAGE, 0, R.string.menu_new_image);
		
		menu.add(0, MENU_SCRAMBLE, 0, R.string.menu_scramble);
		menu.add(0, MENU_CHANGE_TILING, 0, R.string.menu_change_tiling);
		menuShowNumbers = menu.add(0, MENU_SHOW_NUMBERS, 0, "");
		menu.add(0, MENU_STATS, 0, R.string.menu_stats);
		menu.add(0, MENU_ABOUT, 0, R.string.menu_about);

		menu.add(0, MENU_MAIN, 0, R.string.menu_main);

		updateMenuShowNumbers();
		
		return true;
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		return onOptionsItemSelected(item);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case MENU_SCRAMBLE:
				scramble();
				return true;

			case MENU_NEW_IMAGE:
				newImage();
				return true;


			case MENU_CHANGE_TILING:
				changeTiling();
				return true;

			case MENU_SHOW_NUMBERS:
				toggleShowNumbers();
				return true;

			case MENU_STATS:
				showStats();
				return true;
				
			case MENU_ABOUT:
				showAbout();
				return true;

			case MENU_MAIN:
				showMain();
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void showMain() {
		Intent i = new Intent(PuzzleActivity.this, MainActivity.class);
		startActivity(i);
	}

	protected SharedPreferences getPreferences()
	{
		return getSharedPreferences(PuzzleActivity.class.getName(), Activity.MODE_PRIVATE);
	}
	@Override
	protected void onStop()
	{
		super.onStop();

		savePreferences();
	}

	public PuzzleStats updateStats()
	{
		SharedPreferences prefs = getPreferences();
		Editor editor = prefs.edit();

		int i = (expert ? 10000 : 0) +
				Math.min(puzzle.getWidth(), puzzle.getHeight()) * 100 +
				Math.max(puzzle.getWidth(), puzzle.getHeight());
		String index = String.valueOf(i);
		
		int plays = prefs.getInt(KEY_PLAYS_PREFIX + index, 0);
		int best = prefs.getInt(KEY_MOVE_BEST_PREFIX + index, 0);
		float avg = prefs.getFloat(KEY_MOVE_AVERAGE_PREFIX + index, 0);

		plays++;
		boolean isNewBest = best == 0 || best > puzzle.getMoveCount();

		if(isNewBest)
		{
			best = puzzle.getMoveCount();
		}

		avg = (avg * (plays - 1) + puzzle.getMoveCount()) / (float) plays;

		editor.putInt(KEY_PLAYS_PREFIX + index, plays);
		editor.putInt(KEY_MOVE_BEST_PREFIX + index, best);
		editor.putFloat(KEY_MOVE_AVERAGE_PREFIX + index, avg);

		editor.commit();

		return new PuzzleStats(plays, best, avg, isNewBest);
	}

	protected ShowNumbers intToShowNumbers(int i)
	{
		switch(i)
		{
			case 2:
				return ShowNumbers.ALL;
				
			case 1:
				return ShowNumbers.SOME;
				
			default:
				return ShowNumbers.NONE;
		}
	}
	
	protected int showNumbersToInt(ShowNumbers showNumbers)
	{
		switch(showNumbers)
		{
			case ALL:
				return 2;
				
			case SOME:
				return 1;
				
			default:
				return 0;
		}
	}
	
	protected boolean loadPreferences()
	{
		SharedPreferences prefs = getPreferences();
		
		try
		{
			view.setShowNumbers(intToShowNumbers(prefs.getInt(KEY_SHOW_NUMBERS, 1)));
	
			String s = prefs.getString(KEY_IMAGE_URI, null);
	
			if(s == null)
			{
				imageUri = null;
			}
			else
			{
				loadBitmap(Uri.parse(s));
			}
	
			int size = prefs.getInt(KEY_PUZZLE_SIZE, 0);
			s = prefs.getString(KEY_PUZZLE, null);
	
			if(size > 0 && s != null)
			{
				String[] tileStrings = s.split("\\;");
	
				if(tileStrings.length / size > 1)
				{
					setPuzzleSize(size, false);
					puzzle.init(puzzleWidth, puzzleHeight);
		
					int[] tiles = new int[tileStrings.length];
		
					for(int i = 0; i < tiles.length; i++)
					{
						try
						{
							tiles[i] = Integer.parseInt(tileStrings[i]);
						}
						catch(NumberFormatException ex)
						{
						}
					}
					
					puzzle.setTiles(tiles);
				}
			}
	
			puzzle.setMoveCount(prefs.getInt(KEY_MOVE_COUNT, 0));
	
			return prefs.contains(KEY_SHOW_NUMBERS);
		}
		catch(ClassCastException ex)
		{
			// ignore broken settings
			return false;
		}
	}

	protected String getResourceBaseUri()
	{
		return "android.resource://" + PuzzleActivity.class.getPackage().getName() + "/"; 
	}

	protected void savePreferences()
	{
		SharedPreferences prefs = getPreferences();
		Editor editor = prefs.edit();
		editor.putInt(KEY_SHOW_NUMBERS, showNumbersToInt(view.getShowNumbers()));

		if(imageUri == null)
		{
			editor.remove(KEY_IMAGE_URI);
		}
		else
		{
			editor.putString(KEY_IMAGE_URI, imageUri.toString());
		}

		StringBuilder sb = null;

		for(int tile: puzzle.getTiles())
		{
			if(sb == null)
			{
				sb = new StringBuilder();
			}
			else
			{
				sb.append(';');
			}

			sb.append(tile);
		}

		editor.putInt(KEY_PUZZLE_SIZE, Math.min(puzzleWidth, puzzleHeight));
		editor.putString(KEY_PUZZLE, sb.toString());
		editor.putInt(KEY_MOVE_COUNT, puzzle.getMoveCount());

		editor.commit();
	}

	private void showStats()
	{
		SharedPreferences prefs = getPreferences();

		int i = (expert ? 10000 : 0) +
				Math.min(puzzle.getWidth(), puzzle.getHeight())  * 100 +
				Math.max(puzzle.getWidth(), puzzle.getHeight());
		String index = String.valueOf(i);
		
		int plays = prefs.getInt(KEY_PLAYS_PREFIX + index, 0);
		int best = prefs.getInt(KEY_MOVE_BEST_PREFIX + index, 0);
		float avg = prefs.getFloat(KEY_MOVE_AVERAGE_PREFIX + index, 0);

		PuzzleStats stats = new PuzzleStats(plays, best, avg, false);
		showStats(stats);
	}

	public void showStats(PuzzleStats stats)
	{
		String type = sizeToString(puzzleWidth, puzzleHeight);

		String msg;

		if(puzzle.isSolved())
		{
			msg = MessageFormat.format(getString(R.string.finished_type_expert_puzzle_in_n_moves), type, expert ? 1 : 0, puzzle.getMoveCount());
		}
		else
		{
			msg = MessageFormat.format(getString(R.string.type_expert_puzzle_n_moves_so_far), type, expert ? 1 : 0, puzzle.getMoveCount());
		}

		msg = MessageFormat.format(getString(R.string.message_stats_best_avg_plays), msg, stats.getBest(), stats.getAvg(), stats.getPlays());
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(!puzzle.isSolved() ? R.string.title_stats : stats.isNewBest() ? R.string.title_new_record : R.string.title_solved);
		builder.setMessage(msg);
		builder.setPositiveButton(R.string.label_ok, null);

		builder.create().show();
	}

	public void showAbout()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.title_about);
		builder.setMessage(R.string.content_about);
		builder.setPositiveButton(R.string.label_ok, null);
		builder.setIcon(R.mipmap.ic_cc);
		AlertDialog dlg = builder.create();
		dlg.show();
	  ((TextView) dlg.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
	}
	
	public void playSound(int soundId)
	{
		MediaPlayer player = MediaPlayer.create(this, soundId);
		player.start();
	}

	public void onFinish()
	{
		PuzzleStats stats = updateStats();
		playSound(stats.isNewBest() ? R.raw.record : R.raw.solved);
		showStats(stats);
	}
	
	public PuzzleView getView()
	{
		return view;
	}
	
	public Puzzle getPuzzle()
	{
		return puzzle;
	}

}
