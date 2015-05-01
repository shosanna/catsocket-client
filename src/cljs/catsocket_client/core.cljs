(ns catsocket-client.core
  (:require
            [cljs.core.async :as async :refer [<! >! put! chan close!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(declare log send do-send on-message on-open on-close)

(defonce app-state (atom {:text "CatSocket client"}))

(def logger (atom true))
(defn stop-logging [] (reset! logger false))
(defn start-logging [] (reset! logger true))

(defn timeout [ms]
  (let [c (chan)]
    (js/setTimeout (fn [] (close! c)) ms)
    c))

(defn should-send? [timestamp]
  (< (+ timestamp 5000) (.valueOf (js/Date.))))

(defn start-timer
  [cat ms]
  (let [keep-looping (atom true)]
    (go
      (loop []
        (log "Inside loop")
        (<! (timeout 1000))

        (let [groups (group-by (comp should-send? :timestamp)
                               (:sent-messages @cat))

              {to-send true, to-keep false} groups]

          (swap! cat assoc :sent-messages (or to-keep {}))

          (doseq [[_ params] to-send]
            (log "RESENDING" params)
            (let [new-params (assoc params :timestamp (.valueOf (js/Date.)))]
              (do-send cat new-params))))

        (when @keep-looping
          (recur))))

    #(reset! keep-looping false)))

(defn log [& args]
  (if @logger
    (.apply (.-log js/console) js/console (clj->js args))))

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

(defn build-socket [host port]
  (js/WebSocket. (str "ws://" host ":" port "/api/ws")))

(defn set-handlers! [cat socket]
  (set! (.-onopen socket) #(on-open cat %))
  (set! (.-onclose socket) #(on-close cat %))
  (set! (.-onmessage socket) #(on-message cat (.parse js/JSON (.-data %)))))

(defn on-open
  [cat event]
  (swap! cat assoc :connected? true)
  (send cat "identify" {}))

(defn on-close
  [cat event]
  (when-not (:force-close? @cat)
    (log "reconnecting")
    (swap! cat assoc :connected? false :identified? false)
    (js/setTimeout (fn []
                     (let [socket (apply build-socket ((juxt :host :port) @cat))]
                       (set-handlers! cat socket)
                       (swap! cat assoc :socket socket))) 2000)))

(defn build
  ([api-key] (init api-key {}))

  ([api-key {:keys [host port] :or {host "localhost", port 9000}}]
   (let [socket (build-socket host port)

         cat (atom {:socket socket
                    :host host
                    :port port
                    :api-key api-key
                    :connected? false
                    :identified? false
                    :force-close? false
                    :queue []
                    :handlers {}
                    :sent-messages {}})]
     (set-handlers! cat socket)
     cat)))

(defn do-send [cat params]
  (if (and (:connected? @cat) (or (:identified? @cat)
                                  (= (:action params) "identify")))
    (do
      (log "sending" (clj->js params))
      (swap! cat update-in [:sent-messages] assoc (:id params) params)
      (.send (:socket @cat) (stringify params)))
    (do
      ;; (log "enqueing")
      (swap! cat update-in [:queue] conj params))))

(defn send [cat action data]
  (let [params {:api_key (:api-key @cat)
                :user (user)
                :action action
                :data data
                :id (guid)
                :timestamp (.getTime (js/Date.))}]
    (do-send cat params)))

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

(defn close [cat]
  (swap! cat assoc :force-close? true)
  (log "Closing the socket.. Bye!")
  (.close (:socket @cat)))

(defn init [api-key host port]
  (let [cat (build api-key)]
    (reify
      Object
      (join [this room fn] (join cat room fn))
      (leave [this room] (leave cat room))
      (broadcast [this room msg] (broadcast cat room msg))
      (close [this] (close cat)))))

(defn main []
  (set! (.-catsocket js/window) (.-core js/catsocket_client))
  ;; (let [cat (init "foo")]
  ;;   (set! (.-nuf js/window) cat)
  ;;   (set! (.-su js/window) (:socket @cat))
  ;;   (join cat "test" #(log %))
  ;;   (broadcast cat "test" "hello!"))
  )
