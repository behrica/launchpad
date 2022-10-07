(ns lambdaisland.launchpad.watcher
  "Higher level wrapper around Beholder.

  Beholder watches directories, not files. We want to watch specific files in
  specific directories. This can be done with [[watch!]], which will start the
  minimum number of watchers to cover all directories, and will dispatch to the
  right handler based on the changed file."
  (:require [nextjournal.beholder :as beholder]
            [clojure.java.io :as io])
  (:import java.util.regex.Pattern
           java.nio.file.LinkOption
           java.nio.file.Files
           java.nio.file.Paths
           java.nio.file.Path
           java.io.File))

(defonce watchers (atom nil))

(defn canonical-path [p]
  (.getCanonicalPath (io/file p)))

(defn parent-path [p]
  (.getParent (io/file (canonical-path p))))

(require 'clojure.pprint)

(defn watch!
  "Watch a number of files, takes a map from filename (string) to
  handler (receives a map with `:type` and `:path`, as with Beholder)."
  [file->handler]
  (let [file->handler (update-keys file->handler canonical-path)
        directories (distinct (map parent-path (keys file->handler)))
        ;; in case of nested directories, only watch the top-most one
        directories (remove (fn [d]
                              (some #(and (not= d %)
                                          (.startsWith d %)) directories))
                            directories)]
    (swap! watchers
           (fn [w]
             (when w
               (run! beholder/stop w))
             (doall
              (for [dir directories]
                (beholder/watch
                 (fn [{:keys [type path] :as event}]
                   (if-let [f (get file->handler (str path))]
                     (try
                       (f event)
                       (catch Exception e
                         (prn e)))))
                 (str dir))))))))

(comment
  (watch!
   {"/home/arne/Gaiwan/slack-widgets/deps.edn" prn
    "/home/arne/Gaiwan/slack-widgets/.env" prn
    "/home/arne/Gaiwan/slack-widgets/.envx" prn
    "/home/arne/Gaiwan/slack-widgets/backend/deps.edn" prn}))
