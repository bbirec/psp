(ns app.core
  (:require reagent.dom
            [app.hello :refer [hello]]))

(defn ^:dev/after-load render
  "Render the toplevel component for this app."
  []
  (reagent.dom/render [hello] (.getElementById js/document "app")))

(defn ^:export main
  "Run application startup logic."
  []
  (render))
