(ns app.ui
  (:require [reagent.core :as r]
            reagent.dom
            [goog.events :as events]
            goog.net.EventType
            [goog.events.FileDropHandler]
            [app.log :as log]))

(defn drop-container
  []
  (r/create-class
   {:display-name "drop-container"
    :component-did-mount
    (fn [this]
      (-> (reagent.dom/dom-node this)
          (events/FileDropHandler. true)
          (.listen
           events/FileDropHandler.EventType.DROP
           (fn [^js e]
             (let [on-file (get-in (r/argv this)
                                   [1 :on-file])
                   files (-> e .getBrowserEvent .-dataTransfer .-files)]
               (when (and on-file files)
                 (on-file files)))))))

    :component-will-unmount
    (fn [this]
      (let [el (reagent.dom/dom-node this)]
        (events/removeAll el events/FileDropHandler.EventType.DROP)))

    :reagent-render
    (fn [opt & children]
      (into [:div
             (dissoc opt
                     :on-file)]
            children))}))

(defn click-drop-container
  [opt & children]
  (let [input (r/atom nil)]
    (fn [opt & children]
      (let [on-click (fn [e]
                       (.click @input))]
        (into [drop-container
               (-> opt
                   (dissoc :accept)
                   (assoc :on-click on-click))]
              (conj children
                    [:input
                     {:ref #(reset! input %1)
                      :style {:display "none"}
                      :accept (:accept opt)
                      :multiple true
                      :type "file"
                      :on-change (fn [e]
                                   (log/debug "file change")
                                   (when-let [on-file (:on-file opt)]
                                     (let [files (-> e .-target .-files)]
                                       (if files
                                         (on-file files)
                                         (log/debug "file not selected.")))))}]))))))
