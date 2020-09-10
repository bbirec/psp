(ns app.log)

(enable-console-print!)

(defn info [& args]
  (apply js/console.log args))

(defn debug [& args]
  (when ^boolean js/goog.DEBUG
    (apply js/console.log args)))

(defn error [& args]
  (apply js/console.error args))
