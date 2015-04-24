(ns catsocket_client.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [catsocket_client.core-test]))

(enable-console-print!)

(defn runner []
  (if (cljs.test/successful?
       (run-tests
        'catsocket_client.core-test))
    0
    1))
