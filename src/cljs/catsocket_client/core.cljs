(ns catsocket-client.core
  (:require
            [cljs.core.async :as async :refer [<! >! put! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce app-state (atom {:text "Hello Chestnut!"}))

(defn main []
  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (dom/h1 nil (str "nuf " (:text app))))))
    app-state
    {:target (. js/document (getElementById "app"))}))

;; (set! (.-title js/document) "uf")
;; (.log js/console (.-title js/document))

(defn on-message [event]
  (.log js/console event))

(defn s4 []
  (let [r (+ 1 (Math/random))
        r2 (* r 0x10000)
        floored (Math/floor r2)]
    (.substring (.toString floored 16) 1)))

(defn guid "Generates a random GUID" []
  (str (s4) (s4) "-" (s4) "-" (s4) "-" (s4) "-" (s4) (s4) (s4)))

(defn ^:export init []
  (let [url "ws://localhost:9000/api/ws"
        socket (js/WebSocket. url)]
    (set! (.-onopen socket) #(log %))
    (set! (.-onmessage socket) on-message)
    socket))


(defn log [& args]
  (.apply (.-log js/console) js/console (clj->js args)))

(let [c (chan)]
  (put! c "hello")
  (go (log (<! c))))

(defn send [socket]
  (let [params {:api_key "foo"
                :user (guid)
                :action "identify"
                :data {}
                :id (guid)
                :timestamp (.getTime (js/Date.))}
        json (clj->js params)
        json-str (.stringify js/JSON json)]
    (.send socket json-str)))
