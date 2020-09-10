(ns app.hello
  (:require [reagent.core :as r]
            reagent.dom
            goog.net.EventType
            [cljs.core.async :as async]

            [app.log :as log]
            [app.ui :as ui]
            [app.xlsx-wrapper :as xlsx])
  (:import [goog.labs.net xhr])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn conv-data
  "Excel데이터 에서 label data로 변형형한다. barcode, name, quantity"
  [data]
  (let [header (:header data)
        items (map #(zipmap header (map str %1))
                   (:items data))]
    items))

(def data_ (r/atom nil))
(def r_ (atom nil))

(defn hello []
  [:div
   (when (nil? @data_)
     [:div
      [ui/click-drop-container
       {:accept ".csv, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/vnd.ms-excel"
        :on-file (fn [f]
                   (go
                     (let [[err data] (async/<! (xlsx/read-file-async (first f)))]
                       (if err
                         (js/alert "Error")
                         (do
                           (reset! data_ data)
                           (when-let [pv ^js @r_]
                             (log/debug err data pv)
                             (.load pv (clj->js (conv-data data)))))))))}
       [:div.empty "엑셀을 업로드해주세요."]]])
   ^{:key 1}
   [:div {:style {:display (when (nil? @data_)
                             "none")}}
    [:perspective-viewer
     {:settings true
      :ref
      (fn [com]
        (reset! r_ com)
        (log/debug "ref" com))}]]])