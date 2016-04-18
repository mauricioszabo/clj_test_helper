(ns lt.plugins.clj-test-helper
  (:require [lt.object :as object]
            [lt.objs.popup :as popup]
            [lt.objs.editor :as editor]
            [lt.objs.editor.pool :as pool]
            [lt.objs.console :as console]
            [lt.objs.tabs :as tabs]
            [lt.objs.command :as cmd])
  (:require-macros [lt.macros :refer [defui behavior]]))

(defn inform-errors [ed failures]
  (cmd/exec! :clear-inline-results)
  (doseq [f failures]
    (object/raise
      ed
      :editor.result
      (str "Expected: " (:expected f)
           "\nActual: " (:actual f)
           "\nMessage: " (:message f))
      {:line (-> f :line dec)})))

(behavior ::test-results
          :triggers #{:editor.eval.clj.result.helper-result :editor.eval.cljs.result.helper-result}
          :reaction (fn [ed res]
                      (if-let [err (or (:stack res) (:ex res))]
                        (popup/popup! {:body err})
                        (let [sexp-str (-> res :results first :result)
                              result (cljs.reader/read-string sexp-str)
                              [errors summary] result]
                          (inform-errors ed errors)
                          (popup/popup! {:body [:div
                                                [:h1 "Test results"]
                                                [:div [:big "Passed: " (:pass summary)]]
                                                [:div [:big "Failed: " (:fail summary)]]
                                                [:div [:big "Errors: " (:error summary)]]]})))))
(defn eval-test []
  (let [ed (pool/last-active)]
    (object/raise ed :eval)
    (object/raise ed :eval.custom "
(let [failures (atom [])
      summary (atom nil)
      r (fn [msg]
          (case (:type msg)
            :fail (do
                     (swap! failures conj msg)
                     (clojure.test/inc-report-counter :fail))
            :error (let [trace (.getStackTrace (Thread/currentThread))
                         line (->> (remove #(re-find #\"^.?(java.lang|clojure.test)\" (str %)) trace)
                                   second
                                   str
                                   (re-find #\":\\d+\")
                                   (drop 1)
                                   (apply str))]
                     (println (-> msg :actual))
                     (swap! failures conj (-> msg
                                              (update-in [:actual] str)
                                              (assoc :line line)))
                     (clojure.test/inc-report-counter :error))
            :pass (clojure.test/inc-report-counter :pass)
            :summary (reset! summary msg)
            nil))]

  (binding [clojure.test/report r]
    (clojure.test/run-all-tests
      (re-pattern (str \"^\" *ns* \".*\")))
    [@failures @summary]))"
                  {:result-type :helper-result})))

(editor/->cm-ed (pool/last-active))

(cmd/command {:command ::run-clojure-tests
              :desc "Test: Run Clojure Tests"
              :exec (fn [] (eval-test))})
