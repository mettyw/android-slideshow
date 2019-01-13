package link.standen.michael.slideshow.model;

import android.content.SharedPreferences;
import android.os.FileObserver;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import link.standen.michael.slideshow.ImageActivity;
import link.standen.michael.slideshow.model.FileItem;
import link.standen.michael.slideshow.util.FileItemHelper;

public class FileListHandler {

    private static final String TAG = FileListHandler.class.getName();

    private static boolean STOP_ON_COMPLETE;
    private static boolean REVERSE_ORDER;
    private static boolean RANDOM_ORDER;
    private static boolean WATCH_DIRECTORY;

    private final ImageActivity imageActivity;
    private File baseDir;
    private List<FileItem> fileList = new ArrayList<FileItem>();

    private int imagePosition;

    DirectoryObserver observer;

    public FileListHandler(ImageActivity imageActivity) {
        this.imageActivity = imageActivity;
    }

    public void setBaseDir(String directory) {
        synchronized ( fileList ) {
            if (baseDir != null) {
                // FIXME also stop watching on STOP_ON_COMPLETE!
                observer.stopWatching();
                observer.close();
            }
            baseDir = new File(directory);

            loadFileList();

            observer = new DirectoryObserver(directory);
            observer.startWatching();
        }
    }

    private void loadFileList() {
        // FIXME previous code had parameter to include image from subdirs, but was only true when path had peen passed via extra of the intent
        boolean includeSubDirectories = false;
        fileList = (new FileItemHelper(imageActivity).getFileList(baseDir.getAbsolutePath(), false, includeSubDirectories));
        applyPreferences();
    }

    private void reloadFileList() {
        synchronized ( fileList ) {
            String currentImage = getCurrentImage().getPath();
            loadFileList();
            setPositionFromLastImage(currentImage);
        }
    }

    public void setPositionFromLastImage(String imagePath) {
        if (imagePath == null) {
            return;
        }
        if (fileList.size() <= 1) {
            return;
        }

        for (int i = 0; i < fileList.size(); i++) {
            if (imagePath.equals(fileList.get(i).getPath())) {
                // cyclic shift of the complete list, so that item with positon #0 is the one we want
                Collections.rotate(fileList, -i);
                imagePosition = i;
                break;
            }
        }
        Log.v(TAG, String.format("First item is: %s", fileList.get(0).getPath()));
    }

    public int getSize() {
        return fileList.size();
    }

    public FileItem getCurrentImage() {
        return fileList.get(imagePosition);
    }

    public void removeCurrentImage() {
        FileItem current = getCurrentImage();
        fileList.remove(current);
        imagePosition = getSanePosition(imagePosition);
    }

    /**
     * Show the next image.
     */
    public FileItem nextImage(boolean forwards, boolean preload) {
        int current = imagePosition;
        int newPosition = imagePosition;
        do {
            newPosition += forwards ? 1 : -1;
            newPosition = getSanePosition(newPosition);
            /*if (newPosition == current) {
                // Looped. Exit
                imageActivity.onBackPressed();
                return;
            }*/
        } while (!FileItemHelper.isImage(fileList.get(newPosition)));
        if (!preload) {
            imagePosition = newPosition;
        }
        FileItem item = fileList.get(newPosition);
        return item;
    }

    private int getSanePosition(int newPosition) {
        if (newPosition < 0) {
            if (STOP_ON_COMPLETE) {
                return 0;
            } else {
                return fileList.size() - 1;
            }
        }
        if (newPosition >= fileList.size()) {
            if (STOP_ON_COMPLETE) {
                return fileList.size() - 1;
            } else {
                return 0;
            }
        }
        return newPosition;
    }

    public boolean isPositionLast() {
        // FIXME methos needs better name
        return STOP_ON_COMPLETE && (imagePosition == fileList.size() - 1);
    }

    public boolean isPositionFirst() {
        return imagePosition == 0;
    }

    public void loadPreferences(SharedPreferences preferences) {
        STOP_ON_COMPLETE = preferences.getBoolean("stop_on_complete", false);
        REVERSE_ORDER = preferences.getBoolean("reverse_order", false);
        RANDOM_ORDER = preferences.getBoolean("random_order", false);
        // FIXME
        WATCH_DIRECTORY = true;
        applyPreferences();
    }

    private void applyPreferences() {
        if (fileList.size() == 0) return;
        if (RANDOM_ORDER) {
            Collections.shuffle(fileList);
            // FIXME what shall be start positon now?
        } else {
            Collections.sort(fileList);
        }
        if (REVERSE_ORDER) {
            Collections.reverse(fileList);
        }
    }

    private class DirectoryObserver extends FileObserver {
        private static final int mask = (FileObserver.CREATE |
                FileObserver.DELETE |
                FileObserver.DELETE_SELF |
                FileObserver.CREATE |
                FileObserver.MODIFY |
                FileObserver.MOVED_FROM |
                FileObserver.MOVED_TO);

        public DirectoryObserver(String root) {
            super(root, mask);

            if (!root.endsWith(File.separator)) {
                root += File.separator;
            }
        }

        public void onEvent(int event, String path) {
            // return on events we are not interested in
            if ((event & mask) == 0) return;
            Log.d(TAG, "change detected:" + path);

            reloadFileList();
        }

        public void close() {
            super.finalize();
        }

    }
}