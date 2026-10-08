package com.riyadm.apkrepacker.task.base

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.riyadm.apkrepacker.task.base.Constant.Status

abstract class CoroutinesAsyncTask<Params, Progress, Result>{

    val TAG = "CoroutinesAsyncTask"
    var status: Status = Status.PENDING
    abstract fun doInBackground(vararg params: Params?): Result
    open fun onProgressUpdate(vararg values: Progress?) {}
    open fun onPostExecute(result: Result?) {}
    open fun onPreExecute() {}
    open fun onCancelled(result: Result?) {}
    protected var isCancelled = false

    fun execute(vararg params: Params){

        if (status != Status.PENDING) {
            when (status) {
                Status.RUNNING -> throw IllegalStateException("Cannot execute task:" + " the task is already running.")
                Status.FINISHED -> throw IllegalStateException("Cannot execute task:"
                        + " the task has already been executed "
                        + "(a task can be executed only once)")
                Status.PENDING -> TODO()
            }
        }

        status = Status.RUNNING

        // it can be used to setup UI - it should have access to Main Thread
        GlobalScope.launch(Dispatchers.Main){
            onPreExecute()
        }

        // doInBackground works on background thread(default)
        GlobalScope.launch(Dispatchers.IO){
            // GlobalScope has no exception handler, so anything thrown here (an OutOfMemoryError
            // while decoding a huge apk, a bug) would reach the thread's uncaught-exception handler
            // and take the whole app down. Contain it: the task ends with a null result instead.
            val result = try {
                doInBackground(*params)
            } catch (t: Throwable) {
                Log.e(TAG, "doInBackground failed", t)
                null
            }
            status = Status.FINISHED
            withContext(Dispatchers.Main){
                // onPostExecute works on main thread to show output
                Log.d(TAG,"after do in back "+status.name+"--"+isCancelled)
                if (!isCancelled){
                    try { onPostExecute(result) } catch (t: Throwable) { Log.e(TAG, "onPostExecute failed", t) }
                }
            }
        }
    }

    fun cancel(mayInterruptIfRunning : Boolean){
        isCancelled = true
        status = Status.FINISHED
        GlobalScope.launch(Dispatchers.Main){
            // onPostExecute works on main thread to show output
            Log.d(TAG,"after cancel "+status.name+"--"+isCancelled)
            onPostExecute(null)
        }
    }

    fun publishProgress(vararg progress: Progress) {
        //need to update main thread
        GlobalScope.launch(Dispatchers.Main){
            if (!isCancelled){
                try { onProgressUpdate(*progress) } catch (t: Throwable) { Log.e(TAG, "onProgressUpdate failed", t) }
            }
        }
    }
}