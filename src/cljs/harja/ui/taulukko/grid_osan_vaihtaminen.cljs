(ns harja.ui.taulukko.grid-osan-vaihtaminen
  (:require [harja.ui.taulukko.solu :as solu]
            [harja.ui.taulukko.grid-osa-protokollat :as gop]
            [harja.ui.taulukko.solu-protokollat :as sp]))

(defn tyhja->teksti
  ([tyhja parametrit] (tyhja->teksti tyhja parametrit {:fmt identity}))
  ([tyhja parametrit {:keys [fmt]}]
   {:pre [(instance? solu/Tyhja tyhja)
          ;; TODO parametrit spec
          ]
    :post [(instance? solu/Teksti %)]}
   (-> (solu/->Teksti (gop/id tyhja) parametrit)
       (sp/lisaa-fmt fmt)
       (merge (dissoc tyhja :id)))))

(defn tyhja->laajenna
  ([tyhja aukaise-fn auki-alussa? parametrit] (tyhja->laajenna tyhja aukaise-fn auki-alussa? parametrit {:fmt identity}))
  ([tyhja aukaise-fn auki-alussa? parametrit {:keys [fmt]}]
   {:pre [(instance? solu/Tyhja tyhja)
          ;; TODO parametrit spec
          ]
    :post [(instance? solu/Laajenna %)]}
   (-> (solu/->Laajenna (gop/id tyhja) aukaise-fn auki-alussa? parametrit)
       (sp/lisaa-fmt fmt)
       (merge (dissoc tyhja :id)))))

(defn tyhja->syote
  ([tyhja toiminnot kayttaytymiset parametrit] (tyhja->syote tyhja toiminnot kayttaytymiset parametrit {:fmt identity :fmt-aktiiviselle identity}))
  ([tyhja toiminnot kayttaytymiset parametrit {:keys [fmt fmt-aktiiviselle]}]
   {:pre [(instance? solu/Tyhja tyhja)
          ;; TODO parametrit spec
          ]
    :post [(instance? solu/Syote %)]}
   (-> (solu/->Syote (gop/id tyhja) toiminnot kayttaytymiset parametrit)
       (sp/lisaa-fmt fmt)
       (sp/lisaa-fmt-aktiiviselle fmt-aktiiviselle)
       (merge (dissoc tyhja :id)))))
