(ns lt.plugins.clj-test-helper
  (:require [lt.object :as object]
            [lt.objs.popup :as popup]
            [lt.objs.editor.pool :as pool]
            [lt.objs.console :as console]
            [lt.objs.tabs :as tabs]
            [lt.objs.command :as cmd])
  (:require-macros [lt.macros :refer [defui behavior]]))

(defn inform-errors [ed sexp-str]
  (cmd/exec! :clear-inline-results)
  (let [failures (cljs.reader/read-string sexp-str)]
    (doseq [f failures]
      (object/raise
        ed
        :editor.result
        (str "Expected: " (:expected f)
             "\nActual: " (:actual f)
             "\nMessage: " (:message f))
        {:line (-> f :line dec)}))))


(behavior ::test-results
          :triggers #{:editor.eval.clj.result.helper-result :editor.eval.cljs.result.helper-result}
          :reaction (fn [ed res]
                      (if-let [err (or (:stack res) (:ex res))]
                        (popup/popup! {:body err})
                        (inform-errors ed (-> res :results first :result)))))

(defn eval-test []
  (let [ed (pool/last-active)]
    (object/raise ed :eval)
    (object/raise ed :eval.custom "
(let [failures (atom [])
      r (fn [msg]
          (case (:type msg)
            (or :fail :error) (do
                                (swap! failures conj msg)
                                (clojure.test/inc-report-counter (:type msg)))
            :pass (clojure.test/inc-report-counter :pass)
            nil))]

  (binding [clojure.test/report r]
    (clojure.test/run-all-tests
      (re-pattern (str \"^\" *ns* \".*\")))
      @failures))"
                  {:result-type :helper-result})))


(cmd/command {:command ::run-clojure-tests
              :desc "Test: Run Clojure Tests"
              :exec (fn [] (eval-test))})
