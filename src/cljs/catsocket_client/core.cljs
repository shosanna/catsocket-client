(ns catsocket-client.core
  (:require
            [cljs.core.async :as async :refer [<! >! put! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce app-state (atom {:text "Hello Chestnut!"}))

(defn log [& args]
  (.apply (.-log js/console) js/console (clj->js args)))

(defn stringify [msg] (.stringify js/JSON (clj->js msg)))

(defn s4 []
  (let [r (+ 1 (Math/random))
        r2 (* r 0x10000)
        floored (Math/floor r2)]
    (.substring (.toString floored 16) 1)))

(defn guid "Generates a random GUID" []
  (str (s4) (s4) "-" (s4) "-" (s4) "-" (s4) "-" (s4) (s4) (s4)))

(declare send on-message)

(def socket (atom nil))
(def connected? (atom false))
(def identified? (atom false))
(def queue (atom []))
(def sent-messages (atom {}))

(defn on-open
  [socket event]
  (reset! connected? true)
  (send socket "identify" {}))

(defn init []
  (let [url "ws://localhost:9000/api/ws"
        s (js/WebSocket. url)]
    (set! (.-onopen s ) (partial on-open s))
    (set! (.-onmessage s ) #(on-message (.parse js/JSON (.-data %))))

    (reset! socket s)))

(defn send [socket action data]
  (let [params {:api_key "foo"
                :user (guid)
                :action action
                :data data
                :id (guid)
                :timestamp (.getTime (js/Date.))}]
    (if @connected?
      (do
        (log "sending via socket, waiting for ACK")
        (swap! sent-messages assoc (:id params) params)
        (.send socket (stringify params)))
      (do
        (log "enqueing")
        (swap! queue conj params)))))

(defn flush-queue []
  (log "identified flushing queue")
  (doseq [item @queue]
    (log "sending" (clj->js item))
    (.send @socket (stringify item)))
  (reset! queue []))

(defn on-message [data]
  (condp = (.-action data)
    "ack" (do
            (let [id (.-id data)]
              (when (= "identify" (get-in @sent-messages [id :action]))
                (reset! identified? true)
                (flush-queue))
              (swap! sent-messages dissoc id)))
    (log "unrecognized message" data)))

(defn join [room]
  (send @socket "join" {:room room}))

(defn leave [room]
  (send @socket "leave" {:room room}))

(defn broadcast [room msg]
  (send @socket "broadcast" {:room room :message msg}))

;;

(defn main []
  (init)
  (join "test")
  (broadcast "test" "hello!")



  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (dom/h1 nil (str "nuf " (:text app))))))
    app-state
    {:target (. js/document (getElementById "app"))}))
