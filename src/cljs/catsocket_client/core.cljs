(ns catsocket-client.core
  (:require
            [cljs.core.async :as async :refer [<! >! put! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce app-state (atom {:text "CatSocket client"}))

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


(defn user
  []
  (let [user "user"
        result (.getItem js/localStorage user)]
    (if result
      result
      (do
        (let [id (guid)]
          (.setItem js/localStorage user id)
          id)))))


(declare send on-message)

(defn on-open
  [cat event]
  (swap! cat assoc :connected? true)
  (send cat "identify" {}))

(defn init
  ([api-key] (init api-key {}))

  ([api-key {:keys [host port] :or {host "localhost", port 9000}}]
   (let [url (str "ws://" host ":" port "/api/ws")
         socket (js/WebSocket. url)
         cat (atom {:socket socket
                    :api-key api-key
                    :connected? false
                    :identified? false
                    :queue []
                    :handlers {}
                    :sent-messages {}})]
     (set! (.-onopen socket) #(on-open cat %))
     (set! (.-onmessage socket) #(on-message cat (.parse js/JSON (.-data %))))

     cat)))

(defn send [cat action data]
  (let [params {:api_key (:api-key @cat)
                :user (user)
                :action action
                :data data
                :id (guid)
                :timestamp (.getTime (js/Date.))}]
    (if (:connected? @cat)
      (do
        (log "sending" (clj->js params))
        (swap! cat update-in [:sent-messages] assoc (:id params) params)
        (.send (:socket @cat) (stringify params)))
      (do
        (log "enqueing")
        (swap! cat update-in [:queue] conj params)))))

(defn flush-queue [cat]
  (log "identified, flushing queue")

  (let [q (:queue @cat)]
    (swap! cat assoc :queue [])

    (doseq [item q]
      (log "sending" (clj->js item))
      (.send (:socket @cat) (stringify item)))))

(defn on-message [cat data]
  (condp = (.-action data)
    "ack" (do
            (let [id (.-id data)]
              (when (= "identify" (get-in @cat [:sent-messages id :action]))
                (swap! cat assoc :identified? true)
                (flush-queue cat))

              (log "ACK received: " data)
              (swap! cat update-in [:sent-messages] dissoc id)))
    "message" (do
                (let [room (.-room (.-data data))
                      f (get-in @cat [:handlers room])]
                  (if (re-find #"^function" (str (type f)))
                    (f (.-message (.-data data)))
                    (log "Handler is not a function" f))))
    (log "unrecognized message" data)))

(defn join [cat room f]
  (swap! cat update-in [:handlers] assoc room f)
  (send cat "join" {:room room}))

(defn leave [cat room]
  (send cat "leave" {:room room}))

(defn broadcast [cat room msg]
  (send cat "broadcast" {:room room :message msg}))

;;

(defn main []
  (let [cat (init "foo-key")]
    (log cat)
    (join cat "test" #(log %))
    (log "handlers:" (get-in @cat [:handlers]))
    (broadcast cat "test" "hello!"))


  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (dom/h1 nil (:text app)))))
    app-state
    {:target (. js/document (getElementById "app"))}))
