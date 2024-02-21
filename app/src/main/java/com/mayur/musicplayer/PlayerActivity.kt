package com.mayur.musicplayer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mayur.musicplayer.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity(), ServiceConnection, MediaPlayer.OnCompletionListener {


    companion object {
        lateinit var musicListPA : ArrayList<Music>
        var songPosition: Int = 0
        var isPlaying:Boolean = false
        var musicService: MusicService? = null
        @SuppressLint("StaticFieldLeak")
        lateinit var binding: ActivityPlayerBinding
        var repeat: Boolean = false
        var min15: Boolean = false
        var min30: Boolean = false
        var min60: Boolean = false
        var nowPlayingId: String = ""
        var isFavourite: Boolean = false
        var fIndex: Int = -1
        lateinit var loudnessEnhancer: LoudnessEnhancer
    }


    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(MainActivity.currentTheme[MainActivity.themeIndex])
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if(intent.data?.scheme.contentEquals("content")){
            songPosition = 0
            val intentService = Intent(this, MusicService::class.java)
            bindService(intentService, this, BIND_AUTO_CREATE)
            startService(intentService)
            musicListPA = ArrayList()
            musicListPA.add(getMusicDetails(intent.data!!))
            Glide.with(this)
                .load(getImgArt(musicListPA[songPosition].path))
                .apply(RequestOptions().placeholder(R.drawable.music_player_icon_slash_screen).centerCrop())
                .into(binding.songImgPA)
            binding.songNamePA.text = musicListPA[songPosition].title
        }
        else initializeLayout()

        //audio booster feature
        /*binding.boosterBtnPA.setOnClickListener {
            val customDialogB = LayoutInflater.from(this).inflate(R.layout.audio_booster, binding.root, false)
            val bindingB = AudioBoosterBinding.bind(customDialogB)
            val dialogB = MaterialAlertDialogBuilder(this).setView(customDialogB)
                .setOnCancelListener { playMusic() }
                .setPositiveButton("OK"){self, _ ->
                    loudnessEnhancer.setTargetGain(bindingB.verticalBar.progress * 100)
                    playMusic()
                    self.dismiss()
                }
                .setBackground(ColorDrawable(0x803700B3.toInt()))
                .create()
            dialogB.show()

            bindingB.verticalBar.progress = loudnessEnhancer.targetGain.toInt()/100
            bindingB.progressText.text = "Audio Boost\n\n${loudnessEnhancer.targetGain.toInt()/10} %"
            bindingB.verticalBar.setOnProgressChangeListener {
                bindingB.progressText.text = "Audio Boost\n\n${it*10} %"
            }
            setDialogBtnBackground(this, dialogB)
        }*/

        binding.backBtnPA.setOnClickListener { finish() }
        binding.playPauseBtnPA.setOnClickListener{ if(isPlaying) pauseMusic() else playMusic() }
        binding.previousBtnPA.setOnClickListener { prevNextSong(increment = false) }
        binding.nextBtnPA.setOnClickListener { prevNextSong(increment = true) }
        binding.seekBarPA.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if(fromUser) {
                    musicService!!.mediaPlayer!!.seekTo(progress)
                    musicService!!.showNotification(if(isPlaying) R.drawable.pause_icon else R.drawable.play_icon)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            /*{
                // Increase thumb size when touch starts
                increaseThumbBoldness(binding.seekBarPA)
            }*/
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            /*{
                // Restore thumb size when touch ends
                restoreThumbBoldness(binding.seekBarPA)
            }*/
        })
        binding.repeatBtnPA.setOnClickListener {
            if(!repeat){
                repeat = true
                val typedValue = TypedValue()
                // Resolve the theme attribute to obtain the color resource ID
                theme.resolveAttribute(R.attr.themeColor, typedValue, true)
                // Get the color resource ID
                val colorResId = typedValue.resourceId
                // Use ContextCompat.getColor with the resolved color resource ID
                val color = ContextCompat.getColor(this, colorResId)
                // Set the color filter
                binding.repeatBtnPA.setColorFilter(color)
            }else{
                repeat = false
                binding.repeatBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.black))
            }
        }
        binding.equalizerBtnPA.setOnClickListener {
            try {
                val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                eqIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, musicService!!.mediaPlayer!!.audioSessionId)
                eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, baseContext.packageName)
                eqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                startActivityForResult(eqIntent, 13)
            }catch (e: Exception){Toast.makeText(this,  "Equalizer Feature not Supported!!", Toast.LENGTH_SHORT).show()}
        }
        binding.timerBtnPA.setOnClickListener {
            val timer = min15 || min30 || min60
            if(!timer){
                val typedValue = TypedValue()
                // Resolve the theme attribute to obtain the color resource ID
                theme.resolveAttribute(R.attr.themeColor, typedValue, true)
                // Get the color resource ID
                val colorResId = typedValue.resourceId
                // Use ContextCompat.getColor with the resolved color resource ID
                val color = ContextCompat.getColor(this, colorResId)
                // Set the color filter
                binding.timerBtnPA.setColorFilter(color)
                showBottomSheetDialog()
            }
            else {
                val builder = MaterialAlertDialogBuilder(this)
                builder.setTitle("Stop Timer")
                    .setMessage("Do you want to stop timer?")
                    .setPositiveButton("Yes") { _, _ ->
                        min15 = false
                        min30 = false
                        min60 = false
                        binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.black))
                    }
                    .setNegativeButton("No"){dialog, _ ->
                        dialog.dismiss()
                        val typedValue = TypedValue()
                        // Resolve the theme attribute to obtain the color resource ID
                        theme.resolveAttribute(R.attr.themeColor, typedValue, true)
                        // Get the color resource ID
                        val colorResId = typedValue.resourceId
                        // Use ContextCompat.getColor with the resolved color resource ID
                        val color = ContextCompat.getColor(this, colorResId)
                        // Set the color filter
                        binding.timerBtnPA.setColorFilter(color)
                    }
                val customDialog = builder.create()
                customDialog.show()
                setDialogBtnBackground(this, customDialog)
            }
        }
        binding.shareBtnPA.setOnClickListener {
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.type = "audio/*"
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(musicListPA[songPosition].path))
            startActivity(Intent.createChooser(shareIntent, "Sharing Music File!!"))

        }
        binding.favouriteBtnPA.setOnClickListener {
            fIndex = favouriteChecker(musicListPA[songPosition].id)
            if(isFavourite){
                isFavourite = false
                binding.favouriteBtnPA.setImageResource(R.drawable.favourite_empty_icon)
                FavouriteActivity.favouriteSongs.removeAt(fIndex)
            } else{
                isFavourite = true
                binding.favouriteBtnPA.setImageResource(R.drawable.favourite_icon)
                FavouriteActivity.favouriteSongs.add(musicListPA[songPosition])
            }
            FavouriteActivity.favouritesChanged = true
            isFavourite = !isFavourite // Toggle the state

            val iconColor = if (isFavourite) {
                ContextCompat.getColor(this, android.R.color.black)
            } else {
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            }
            binding.favouriteBtnPA.setColorFilter(iconColor)
        }
    }
    /*private fun increaseThumbBoldness(seekBar: SeekBar?) {
        val thumb = seekBar?.thumb
        thumb?.alpha = 0.7f // Make thumb slightly transparent when touched
        thumb?.setBounds(0, 0, thumb.intrinsicWidth + 20, thumb.intrinsicHeight + 20)
        seekBar?.thumb = thumb
    }

    private fun restoreThumbBoldness(seekBar: SeekBar?) {
        val thumb = seekBar?.thumb
        thumb?.alpha = 1f // Fully opaque thumb when not touched
        thumb?.setBounds(0, 0, thumb.intrinsicWidth, thumb.intrinsicHeight)
        seekBar?.thumb = thumb
    }*/
    //Important Function
    private fun initializeLayout(){
        songPosition = intent.getIntExtra("index", 0)
        when(intent.getStringExtra("class")){
            "NowPlaying"->{
                setLayout()
                binding.tvSeekBarStart.text = formatDuration(musicService!!.mediaPlayer!!.currentPosition.toLong())
                binding.tvSeekBarEnd.text = formatDuration(musicService!!.mediaPlayer!!.duration.toLong())
                binding.seekBarPA.progress = musicService!!.mediaPlayer!!.currentPosition
                binding.seekBarPA.max = musicService!!.mediaPlayer!!.duration
                if(isPlaying) binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
                else binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
            }
            "MusicAdapterSearch"-> initServiceAndPlaylist(MainActivity.musicListSearch, shuffle = false)
            "MusicAdapter" -> initServiceAndPlaylist(MainActivity.MusicListMA, shuffle = false)
            "FavouriteAdapter"-> initServiceAndPlaylist(FavouriteActivity.favouriteSongs, shuffle = false)
            "MainActivity"-> initServiceAndPlaylist(MainActivity.MusicListMA, shuffle = true)
            "FavouriteShuffle"-> initServiceAndPlaylist(FavouriteActivity.favouriteSongs, shuffle = true)
            "PlaylistDetailsAdapter"->
                initServiceAndPlaylist(PlaylistActivity.musicPlaylist.ref[PlaylistDetails.currentPlaylistPos].playlist, shuffle = false)
            "PlaylistDetailsShuffle"->
                initServiceAndPlaylist(PlaylistActivity.musicPlaylist.ref[PlaylistDetails.currentPlaylistPos].playlist, shuffle = true)
            "PlayNext"->initServiceAndPlaylist(PlayNext.playNextList, shuffle = false, playNext = true)
        }
        if (musicService!= null && !isPlaying) playMusic()
    }

    private fun setLayout(){
        fIndex = favouriteChecker(musicListPA[songPosition].id)
        Glide.with(applicationContext)
            .load(musicListPA[songPosition].artUri)
            .apply(RequestOptions().placeholder(R.drawable.music_player_icon_slash_screen).centerCrop())
            .into(binding.songImgPA)
        binding.songNamePA.text = musicListPA[songPosition].title
        binding.songNamePA.isSelected = true
        if(repeat) binding.repeatBtnPA.setColorFilter(ContextCompat.getColor(applicationContext, R.color.purple_500))
        if(min15 || min30 || min60) binding.timerBtnPA.setColorFilter(ContextCompat.getColor(applicationContext, R.color.purple_500))
        if(isFavourite) binding.favouriteBtnPA.setImageResource(R.drawable.favourite_icon)
        else binding.favouriteBtnPA.setImageResource(R.drawable.favourite_empty_icon)
        val img = getImgArt(musicListPA[songPosition].path)
        val image = if (img != null && img.isNotEmpty()) {
            BitmapFactory.decodeByteArray(img, 0, img.size)
        } else {
            BitmapFactory.decodeResource(
                resources,
                R.drawable.music_player_icon_slash_screen
            )
        }
        val dominantColor = getDominantColorFromImage(image)
        //setBackgroundFromImage(image)
        setGradientBackground(dominantColor)
        //NowPlaying.backgroundColor = dominantColor
        //window.decorView.background = drawable
        /*val bgColor = getMainColor(image)
        val gradient = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xFFFFFF, bgColor))
        binding.root.background = gradient*/
        //window?.statusBarColor = dominantColor
    }

    private fun createMediaPlayer(){
        try {
            if (musicService!!.mediaPlayer == null) musicService!!.mediaPlayer = MediaPlayer()
            musicService!!.mediaPlayer!!.reset()
            musicService!!.mediaPlayer!!.setDataSource(musicListPA[songPosition].path)
            musicService!!.mediaPlayer!!.prepare()
            binding.tvSeekBarStart.text = formatDuration(musicService!!.mediaPlayer!!.currentPosition.toLong())
            binding.tvSeekBarEnd.text = formatDuration(musicService!!.mediaPlayer!!.duration.toLong())
            binding.seekBarPA.progress = 0
            binding.seekBarPA.max = musicService!!.mediaPlayer!!.duration
            musicService!!.mediaPlayer!!.setOnCompletionListener(this)
            nowPlayingId = musicListPA[songPosition].id
            playMusic()
            loudnessEnhancer = LoudnessEnhancer(musicService!!.mediaPlayer!!.audioSessionId)
            loudnessEnhancer.enabled = true
        }catch (e: Exception){Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show()}
    }

    private fun playMusic(){
        isPlaying = true
        musicService!!.mediaPlayer!!.start()
        binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
        musicService!!.showNotification(R.drawable.pause_icon)
    }

    private fun pauseMusic(){
        isPlaying = false
        musicService!!.mediaPlayer!!.pause()
        binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
        musicService!!.showNotification(R.drawable.play_icon)


    }
    private fun prevNextSong(increment: Boolean){
        if(increment)
        {
            setSongPosition(increment = true)
            setLayout()
            createMediaPlayer()
        }
        else{
            setSongPosition(increment = false)
            setLayout()
            createMediaPlayer()
        }
    }



    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if(musicService == null){
            val binder = service as MusicService.MyBinder
            musicService = binder.currentService()
            musicService!!.audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            musicService!!.audioManager.requestAudioFocus(musicService, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        createMediaPlayer()
        musicService!!.seekBarSetup()


    }

    override fun onServiceDisconnected(name: ComponentName?) {
        musicService = null
    }

    override fun onCompletion(mp: MediaPlayer?) {
        setSongPosition(increment = true)
        createMediaPlayer()
        setLayout()

        //for refreshing now playing image & text on song completion
        NowPlaying.binding.songNameNP.isSelected = true
        Glide.with(applicationContext)
            .load(musicListPA[songPosition].artUri)
            .apply(RequestOptions().placeholder(R.drawable.music_player_icon_slash_screen).centerCrop())
            .into(NowPlaying.binding.songImgNP)
        NowPlaying.binding.songNameNP.text = musicListPA[songPosition].title
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == 13 || resultCode == RESULT_OK)
            return
    }

    private fun showBottomSheetDialog(){
        val dialog = BottomSheetDialog(this@PlayerActivity)
        dialog.setContentView(R.layout.bottom_sheet_dialog)
        dialog.show()
        dialog.findViewById<LinearLayout>(R.id.min_15)?.setOnClickListener {
            Toast.makeText(baseContext,  "Music will stop after 15 minutes", Toast.LENGTH_SHORT).show()
            val typedValue = TypedValue()
            // Resolve the theme attribute to obtain the color resource ID
            theme.resolveAttribute(R.attr.themeColor, typedValue, true)
            // Get the color resource ID
            val colorResId = typedValue.resourceId
            // Use ContextCompat.getColor with the resolved color resource ID
            val color = ContextCompat.getColor(this, colorResId)
            // Set the color filter
            binding.timerBtnPA.setColorFilter(color)
            min15 = true
            Thread{Thread.sleep((15 * 60000).toLong())
                if(min15) exitApplication()}.start()
            dialog.dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.min_30)?.setOnClickListener {
            Toast.makeText(baseContext,  "Music will stop after 30 minutes", Toast.LENGTH_SHORT).show()
            val typedValue = TypedValue()
            // Resolve the theme attribute to obtain the color resource ID
            theme.resolveAttribute(R.attr.themeColor, typedValue, true)
            // Get the color resource ID
            val colorResId = typedValue.resourceId
            // Use ContextCompat.getColor with the resolved color resource ID
            val color = ContextCompat.getColor(this, colorResId)
            // Set the color filter
            binding.timerBtnPA.setColorFilter(color)
            min30 = true
            Thread{Thread.sleep((30 * 60000).toLong())
                if(min30) exitApplication()}.start()
            dialog.dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.min_60)?.setOnClickListener {
            Toast.makeText(baseContext,  "Music will stop after 60 minutes", Toast.LENGTH_SHORT).show()
            val typedValue = TypedValue()
            // Resolve the theme attribute to obtain the color resource ID
            theme.resolveAttribute(R.attr.themeColor, typedValue, true)
            // Get the color resource ID
            val colorResId = typedValue.resourceId
            // Use ContextCompat.getColor with the resolved color resource ID
            val color = ContextCompat.getColor(this, colorResId)
            // Set the color filter
            binding.timerBtnPA.setColorFilter(color)
            min60 = true
            Thread{Thread.sleep((60 * 60000).toLong())
                if(min60) exitApplication()}.start()
            dialog.dismiss()
        }
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getMusicDetails(contentUri: Uri): Music{
        var cursor: Cursor? = null
        try {
            val projection = arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION)
            cursor = this.contentResolver.query(contentUri, projection, null, null, null)
            val dataColumn = cursor?.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = cursor?.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            cursor!!.moveToFirst()
            val path = dataColumn?.let { cursor.getString(it) }
            val duration = durationColumn?.let { cursor.getLong(it) }!!
            return Music(id = "Unknown", title = path.toString(), album = "Unknown", artist = "Unknown", duration = duration,
                artUri = "Unknown", path = path.toString())
        }finally {
            cursor?.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(musicListPA[songPosition].id == "Unknown" && !isPlaying) exitApplication()
    }
    private fun initServiceAndPlaylist(playlist: ArrayList<Music>, shuffle: Boolean, playNext: Boolean = false){
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, this, BIND_AUTO_CREATE)
        startService(intent)
        musicListPA = ArrayList()
        musicListPA.addAll(playlist)
        if(shuffle) musicListPA.shuffle()
        setLayout()
        if(!playNext) PlayNext.playNextList = ArrayList()
    }
    // Function to get dominant color from an image
    private fun getDominantColorFromImage(imageBitmap: Bitmap): Int {
        val palette = Palette.from(imageBitmap).generate()

// Filter out swatches with low luminance (black, grey, white, and their shades)
        val filteredSwatches = palette.swatches.filter { swatch ->
            val luminance = ColorUtils.calculateLuminance(swatch.rgb)
            luminance > 0.1 && luminance < 0.9 // Adjust the luminance threshold based on your preference
        }

// Find the dominant color among the filtered swatches
        val dominantSwatch = filteredSwatches.maxByOrNull { it.population }

        if (dominantSwatch != null) {
            return dominantSwatch.rgb
        } else {
            // If no dominant color is found, fetch the dark color from getBrightestColorFromImage
            return getBrightestColorFromImage(imageBitmap)
        }
    }
    private fun setGradientBackground(dominantColor: Int) {
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,intArrayOf(Color.WHITE,dominantColor) // Change Color.WHITE to your default color
        )
        //val color = ColorDrawable()
        // Set the gradient as the background for the root layout or the entire screen
        window.decorView.background = gradient
        window?.statusBarColor = dominantColor
        window?.setBackgroundDrawable(gradient)
    }
    /*
    private fun setBackgroundFromImage(imageBitmap: Bitmap) {
        val palette = Palette.from(imageBitmap).generate()
        val dominantColor = palette.dominantSwatch?.rgb ?: Color.WHITE // Default color if no dominant color found
        val glassColor = Color.argb(150, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor))
    }*/

    private fun getBrightestColorFromImage(imageBitmap: Bitmap): Int {
        val palette = Palette.from(imageBitmap).generate()
        val darkSwatch = palette.swatches
            .filter { swatch ->
                // Exclude black or its shades
                val luminance = ColorUtils.calculateLuminance(swatch.rgb)
                luminance >= 0.2
            }
            .minByOrNull { swatch ->
                // Calculate darkness based on RGB values
                val rgb = swatch.rgb
                val darkness = (Color.red(rgb) * 299 + Color.green(rgb) * 587 + Color.blue(rgb) * 114) / 1000
                darkness
            }
        return darkSwatch?.rgb ?: getDominantColorFromImage(imageBitmap) // Return black if no suitable color is found
    }
}