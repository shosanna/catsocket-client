(ns catsocket-client.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

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

(defn guid []
  (str (s4) (s4) "-" (s4) "-" (s4) "-" (s4) "-" (s4) (s4) (s4)))

(defn init []
  (let [url "ws://localhost:9000/api/ws"
        socket (js/WebSocket. url)]
    (set! (.-onopen socket) #(js/alert %))
    (set! (.-onmessage socket) on-message)

    socket))

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
