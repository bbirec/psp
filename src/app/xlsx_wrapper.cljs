(ns app.xlsx-wrapper
  (:require [app.xlsx :as xlsx]
            [cljs.core.async :as async])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- load [on-load on-error]
  (on-load))

(defn read-file-async [file]
  (let [ch-out (async/chan)]
    (load (fn []
            (try
              (-> (xlsx/read-file-async file)
                  (.then (fn [data]
                           (go (async/>! ch-out [nil data])
                               (async/close! ch-out)))
                         (fn [err]
                           (go (async/>! ch-out [err nil])
                               (async/close! ch-out)))))
              (catch :default err
                (go (async/>! ch-out [err nil])
                    (async/close! ch-out)))))
          (fn []
            (go (async/>! ch-out [(js/Error. "xlsx load failed") nil])
                (async/close! ch-out))))
    ch-out))

(defn read-string-async [data-string]
  (let [ch-out (async/chan)]
    (load (fn []
            (let [result (try
                           [nil (xlsx/read-string data-string)]
                           (catch :default err
                             [err nil]))]
              (go (async/>! ch-out result)
                  (async/close! ch-out))))
          (fn []
            (go (async/>! ch-out [(js/Error. "xlsx load failed") nil])
                (async/close! ch-out))))
    ch-out))

(defn to-file-async [header data-list file-basename]
  (let [ch-out (async/chan)]
    (load (fn []
            (xlsx/to-file header data-list file-basename)
            (go (async/>! ch-out [nil])
                (async/close! ch-out)))
          (fn [err]
            (go (async/>! ch-out [err])
                (async/close! ch-out))))
    ch-out))

(defn arr-to-file-async [data file-basename]
  (let [ch-out (async/chan)]
    (load (fn []
            (xlsx/arr-to-file data file-basename)
            (go (async/>! ch-out [nil])
                (async/close! ch-out)))
          (fn [err]
            (go (async/>! ch-out [err])
                (async/close! ch-out))))
    ch-out))

(defn to-file-multi-sheet-async [sheet-list file-basename]
  (let [ch-out (async/chan)]
    (load (fn []
            (xlsx/to-file-multi-sheet sheet-list file-basename)
            (go (async/>! ch-out [nil])
                (async/close! ch-out)))
          (fn [err]
            (go (async/>! ch-out [err])
                (async/close! ch-out))))
    ch-out))
