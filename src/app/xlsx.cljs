(ns app.xlsx
  (:require [goog.fs.FileReader :as file-reader]
            ;; https://github.com/clojure/clojurescript/wiki/Google-Closure-Library#requiring-a-function
            [goog.string :as gstring]
            goog.string.format
            goog.object
            [oops.core :refer [oget]]
            ["xlsx" :as XLSX]))

(def ^:private basedate (js/Date. 1899 11 30 0 0 0))
;;; https://github.com/SheetJS/js-xlsx/issues/1470 적용 안한 버전.
;; 엑셀 원본 값을 얻기 위해
(def ^:private dnthresh (+ (.getTime basedate)
                           (*  (- (.getTimezoneOffset (js/Date.))
                                  (.getTimezoneOffset basedate))
                               60000)))

(def ^:private days-1462-ms (* 1462 24 60 60 1000))
(def ^:private day-ms (* 24 60 60 1000))
(defn- datenum [v date1904]
  (let [epoch (cond-> (.getTime v)
                date1904 (- days-1462-ms))]
    (/ (- epoch dnthresh)
       day-ms)))

(defn- hotfix-date [date1904? ^js data]
  (if (instance? js/Date data)
    (try
      ;; library가 밷은 js-date는 문제가 많다.
      ;; bug1.
      ;; 버그인지, 의도인지, read할 때 cellDates true 로 줘서 생성하면
      ;; 날짜(로 추정되는) 셀은 js/Date로 생성되는데,
      ;; 생성된 js/Date object가 date1904를 고려하지 않고 있다.
      ;; bug2.
      ;; 1900 이전에 solar 타임 쓰던 국가들에서는 초 단위 오차가 생김.
      ;; https://github.com/SheetJS/js-xlsx/issues/1470
      ;; bug3.
      ;; 타임존이 변경되는 경우를 고려하지 않고 있다.
      ;; 지금 순간의 데이터에 시간을 변경하는거라서, 한 나라에서 타임존이 바뀌는 경우에 문제가 생긴다.
      ;; 한국 1955년~1960년은 +08:30 썼기 때문에 오차가 생김. 써머타임 쓰는 국가도 문제생길 듯
      (let [parsed (XLSX/SSF.parse_date_code
                    (datenum data false)
                    (js-obj "date1904" date1904?))]
        (gstring/format "%04d-%02d-%02d"
                        (.-y parsed)
                        (.-m parsed)
                        (.-d parsed)))
      (catch :default _
        "CONV_ERROR"))
    data))

(defn- convert-js-date-to-string [row-list date1904?]
  (mapv (fn [v]
          (mapv (partial hotfix-date date1904?)
                v))
        row-list))

(defn- get-first-sheet-name [^js wb-js]
  (if-let [sheets (oget wb-js "?Workbook.?Sheets")]
    ;; Workbook prop이 있는 경우. xlsx 파일 파싱
    (->> (js->clj sheets :keywordize-keys true)
         (some (fn [sheet]
                 (when (zero? (:Hidden sheet))
                   (:name sheet)))))
    ;; 없는 경우. csv나 string 파싱.
    (oget wb-js "?.SheetNames" "?0")))

(defn- read-xlsx-first-sheet-as-array [data-arr]
  (let [wb-js (XLSX/read data-arr (js-obj
                                      "type" "array"
                                      "cellDates" true))
        date1904? (oget wb-js "?Workbook.?WBProps.?date1904")]
    (when-let [first-sheet-name (get-first-sheet-name wb-js)]
      (let [sheet (goog.object/getValueByKeys wb-js "Sheets" first-sheet-name)]
        (-> (XLSX/utils.sheet_to_json sheet (js-obj "header" 1))
            (js->clj :keywordize-keys true)
            (convert-js-date-to-string date1904?))))))

(defn read-file-async [file]
  (-> (file-reader/readAsArrayBuffer file)
      (.then (fn [data]
               (let [rows (read-xlsx-first-sheet-as-array data)]
                 {:filename (.-name file)
                  :header (first rows)
                  :items (into [] (rest rows))})))))

;; 01234를 1234로 aggresive하게 변환해버리는 문제가 있어서 raw로 읽도록 함.
;; sheet_to_json이 목표 type을 몰라서 최대한 변환하기 때문이라 sheet_to_json을 쓰면 안됨.
;; 해당 cell을 import할 때 목표 타입에 따라 변환을 다르게 해야함.
;; text: w 필드 사용
;; number: v 필드 사용. (추가로 컨버팅 시도해야함)
;; date: read할 때 cellDates를 꺼야함. v 필드를 hotfix-date로 변환해야 함.
(defn- read-string-as-array [data-string]
  (let [wb-js (XLSX/read data-string (js-obj
                                         "type" "string"
                                         "raw" true))]
    (when-let [first-sheet-name (get-first-sheet-name wb-js)]
      (let [sheet (goog.object/getValueByKeys wb-js "Sheets" first-sheet-name)]
        (-> (XLSX/utils.sheet_to_json sheet (js-obj "header" 1))
            (js->clj :keywordize-keys true))))))

(defn read-string
  "예외 발생 시 exception 날림"
  [data-string]
  (let [rows (read-string-as-array data-string)]
    {:filename "clipboard"
     :header (first rows)
     :items (into [] (rest rows))}))

;;; header [{:key :a :label "키1"} {:key :b :label "키2"}]
;;; data [{:a "value1" :b "value2"} {:a "hello" :b "world"}]
(defn- generate-sheet [header data]
  (let [key-list (map :key header)
        header-row (reduce (fn [aux {key :key label :label}]
                             (assoc aux key label))
                           {}
                           header)
        data-rows (map #(select-keys %1 key-list) data)]
    (XLSX/utils.json_to_sheet (clj->js (cons header-row data-rows))
                                 (clj->js {:header (map :key header)
                                           :skipHeader true}))))

(defn to-file [header data file-basename]
  (let [wb (XLSX/utils.book_new)
        ws (generate-sheet header data)]
    (XLSX/utils.book_append_sheet wb ws "BoxHero")
    (XLSX/writeFile wb (str file-basename ".xlsx"))))

(defn arr-to-file
  "data는 array of arrays"
  [data file-basename]
  (let [wb (XLSX/utils.book_new)
        ws (XLSX/utils.aoa_to_sheet (clj->js data))]
    (XLSX/utils.book_append_sheet wb ws "BoxHero")
    (XLSX/writeFile wb (str file-basename ".xlsx"))))

;;; [{:name "Sheet 1" :header (generate-sheet 참조) :data (generate-sheet 참조)}]
(defn to-file-multi-sheet [sheet-list file-basename]
  (let [wb (XLSX/utils.book_new)]
    (doseq [sheet sheet-list]
      (let [ws (generate-sheet (:header sheet)
                               (:data sheet))]
        (XLSX/utils.book_append_sheet wb
                                         ws
                                         (:name sheet))))
    (XLSX/writeFile wb (str file-basename ".xlsx"))))
