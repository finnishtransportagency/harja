(ns harja.tyokalut.tuck
  (:require [clojure.string :as clj-str]))

(defmacro varmista-kasittelyjen-jarjestys
  "Tämän sisään voi wrapata harja.tyokalut.tuck/get! ja harja.tyokalut.tuck/post!
   kasittlyjä. Kaikki Ajax kutsut lähetetään samaan aikaan, joten niille
   tulevat vastaukset voi olla eri järejstyksessä, kuin tämän sisällä määritetty.
   Tässä kumminkin varmistetaan, että callbackien käsittely järjestys on sama kuin
   Ajax kutsujen järjestys."
  [& kutsut]
  `(let [kutsujen-jarjestys# ~(mapv (fn [palvelukutsu-f]
                                      (let [palvelukutsu-args (rest palvelukutsu-f)
                                            args-count (count palvelukutsu-args)
                                            palvelukutsu (-> palvelukutsu-f first str (clj-str/split #"/"))
                                            palvelukutsu-f-nimi (last palvelukutsu)
                                            kutsu (cond
                                                    (and (= palvelukutsu-f-nimi "post!")
                                                         (= args-count 3)) (first palvelukutsu-args)
                                                    (and (= palvelukutsu-f-nimi "post!")
                                                         (= args-count 4)) (second palvelukutsu-args)
                                                    (and (= palvelukutsu-f-nimi "get!")
                                                         (= args-count 2)) (first palvelukutsu-args)
                                                    (and (= palvelukutsu-f-nimi "get!")
                                                         (= args-count 3)) (second palvelukutsu-args)
                                                    :else (throw (Error. (str "Ei löydetty palvelukutsua, joka pitäisi suorittaa järjestyksessä. " palvelukutsu-f))))]
                                        (when-not (keyword? kutsu)
                                          (throw (Error. (str "Kutsuttavan palvelun pitäisi olla keyword. Saatiin: " kutsu))))
                                        kutsu))
                                    kutsut)
         kutsujen-jarjestys-atomi# (atom kutsujen-jarjestys#)]
     (when-not (every? (fn [~'f]
                         (or (= harja.tyokalut.tuck/post! ~'f)
                             (= harja.tyokalut.tuck/get! ~'f)))
                       ~(mapv first kutsut))
       (throw (~'js/Error. "Jokainen kutsuttava funktio varmista-kasittelyjen-jarjestys makrolle tulisi olla joko harja.tyokalut.tuck/get! tai harja.tyokalut.tuck/post!")))
     (cljs.core.async.macros/go
       (binding [harja.tyokalut.tuck/*kutsu-jarjestys* kutsujen-jarjestys-atomi#]
         (let [kutsut# ~(mapv (fn [palvelukutsu-f]
                                (let [f# (butlast palvelukutsu-f)]
                                  `(apply partial ~f#)))
                              kutsut)
               optiot# ~(mapv (fn [palvelukutsu-f]
                                (last palvelukutsu-f))
                              kutsut)
               kanavat# (repeatedly ~(count kutsut) (cljs.core.async/chan))
               ~'_ (doseq [~'kanava kanavat#]
                   (cljs.core.async/sub harja.tyokalut.tuck/jarjestys-pub :kutsu-kasitelty? ~'kanava))
               kutujen-kanavat# (map (fn [~'kutsu ~'optiot ~'kanava]
                                        (~'kutsu (merge ~'optiot
                                                        {:harja.tyokalut.tuck/jarjestys-sub ~'kanava
                                                         :palauta-kanava? true})))
                                      kutsut#
                                      optiot#
                                      kanavat#)
               kasittelykanava# (cljs.core.async/merge kutujen-kanavat#)]
           (loop [vastaus# (cljs.core.async/<! kasittelykanava#)]
             (if (nil? vastaus#)
               :kutsut-kasitelty
               (recur (cljs.core.async/<! kasittelykanava#)))))))))